import { Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';

import {
  AgentStreamEvent,
  BpmDesignerAgentService,
  DesignerAgentRunStatus
} from './bpm-designer-agent.service';
import { PlanDisplayView, resolvePlanDisplay, stepKindIcon } from './edit-plan-presenter';

import { ComponentProvider } from '../../flow-elements/component-provider';

import { BpmEditorToken } from '../editor/bpm-editor-token';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';

const StageLabels: Record<string, string> = {
  ingest: '理解场景',
  think: '规划中',
  apply: '应用变更',
  validate: '校验',
  repair: '修复',
  await_plan: '等待 Plan 确认',
  await_preview: '等待预览确认',
  await_install: '等待安装插件',
  await_ask: '等待补充说明',
  done: '已完成',
  error: '失败'
};

interface ChatMsg {
  role: 'user' | 'assistant';
  text: string;
  thinking: string[];
}

@Component({
  selector: 'bpm-designer-agent',
  standalone: true,
  imports: [FormsModule, NzButtonModule, NzIconModule, NzInputModule, NzSpinModule, NzTagModule],
  templateUrl: './bpm-designer-agent.component.html',
  styleUrl: './bpm-designer-agent.component.scss'
})
export class BpmDesignerAgentComponent {
  private readonly editor = inject(BpmEditorToken);
  private readonly agentApi = inject(BpmDesignerAgentService);
  private readonly componentProvider = inject(ComponentProvider);
  private readonly nzMessage = inject(NzMessageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly panelOpen = signal(false);
  readonly busy = signal(false);
  readonly inputText = signal('');
  readonly askText = signal('');
  readonly status = signal<DesignerAgentRunStatus | null>(null);
  readonly messages = signal<ChatMsg[]>([]);
  readonly planDisplay = signal<PlanDisplayView | null>(null);
  readonly planTechnicalJson = signal('');
  readonly stepKindIcon = stepKindIcon;

  private streamAbort: AbortController | null = null;
  private assistantIdx = -1;

  readonly bpmProcessId = computed(() => {
    const process = this.editor.getBpmProcess();
    return this.editor.getBpmnId() || process?.id || '';
  });

  readonly stageLabel = computed(() => {
    const stage = this.status()?.stage;
    return stage ? (StageLabels[stage] ?? stage) : '';
  });

  readonly awaitPlan = computed(() => this.status()?.stage === 'await_plan');
  readonly awaitPreview = computed(() => this.status()?.stage === 'await_preview');
  readonly awaitAsk = computed(() => this.status()?.stage === 'await_ask');

  constructor() {
    effect(() => {
      const id = this.bpmProcessId();
      if (!id) {
        this.status.set(null);
        return;
      }
      this.agentApi
        .statusByTarget(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(s => {
          this.status.set(s);
          this.syncPlanDisplay(s);
        });
    });
  }

  send(): void {
    const text = this.inputText().trim();
    const processId = this.bpmProcessId();
    if (!text || !processId || this.busy()) {
      return;
    }
    this.messages.update(list => [...list, { role: 'user', text, thinking: [] }]);
    this.inputText.set('');
    this.busy.set(true);
    this.streamAbort?.abort();
    void this.buildContext().then(ctx => {
      this.streamAbort = this.agentApi.startRunStream(
        {
          scenario: text,
          targetProcessId: processId,
          selectedElementId: ctx.selectedElementId,
          baseBpmnXml: ctx.bpmnXml
        },
        (event, name) => this.handleEvent(event, name),
        err => {
          this.busy.set(false);
          this.nzMessage.error(err instanceof Error ? err.message : 'Agent 失败');
        },
        () => {
          this.busy.set(false);
        }
      );
    });
  }

  confirmPlan(confirmed: boolean): void {
    const runId = this.status()?.runId;
    if (!runId) {
      return;
    }
    this.busy.set(true);
    this.attachContinuationStream(runId);
    this.agentApi
      .confirmPlan(runId, confirmed)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: s => {
          this.status.set(s);
          if (s.candidateXml) {
            void this.editor.importBpmnXml(s.candidateXml);
          }
        },
        error: e => this.nzMessage.error(e?.message ?? '确认失败')
      });
  }

  confirmPreview(confirmed: boolean): void {
    const runId = this.status()?.runId;
    if (!runId) {
      return;
    }
    this.busy.set(true);
    this.agentApi
      .confirmPreview(runId, confirmed)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: s => {
          this.status.set(s);
          if (confirmed) {
            this.nzMessage.success('已保存流程');
          }
        },
        error: e => this.nzMessage.error(e?.message ?? '预览确认失败')
      });
  }

  submitAsk(): void {
    const runId = this.status()?.runId;
    const answer = this.askText().trim();
    if (!runId || !answer) {
      return;
    }
    this.busy.set(true);
    this.attachContinuationStream(runId);
    this.agentApi
      .answer(runId, answer)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: s => {
          this.status.set(s);
          this.askText.set('');
        },
        error: e => this.nzMessage.error(e?.message ?? '提交失败')
      });
  }

  onInputKeydown(ev: KeyboardEvent): void {
    if (ev.key === 'Enter' && !ev.shiftKey) {
      ev.preventDefault();
      this.send();
    }
  }

  private handleEvent(event: AgentStreamEvent, eventName: string): void {
    if (eventName === 'run_started' && 'runId' in event) {
      this.status.set(event as unknown as DesignerAgentRunStatus);
      this.startAssistantBubble();
      return;
    }
    const type = event.type ?? eventName;
    if (type === 'stage') {
      this.appendThinking(`${event.label ?? event.stage}: ${event.detail ?? ''}`);
      this.status.update(s => ({ ...(s ?? { active: true }), stage: event.stage, active: true }));
      return;
    }
    if (type === 'thinking_delta' && event.delta) {
      this.appendThinking(event.delta);
      return;
    }
    if (type === 'tool_start' && event.toolName) {
      this.appendThinking(`🔧 ${event.toolName}${event.argsPreview ? `: ${event.argsPreview}` : ''}`);
      return;
    }
    if (type === 'tool_end' && event.toolName) {
      this.appendThinking(`✓ ${event.toolName}: ${event.summary ?? '完成'}`);
      return;
    }
    if (type === 'validation' && event.issuesJson) {
      this.appendThinking(`校验: ${event.issuesJson}`);
      return;
    }
    if (type === 'text_delta' && event.delta) {
      this.appendAssistantText(event.delta);
      return;
    }
    if (type === 'plan_ready') {
      this.syncPlanDisplayFromEvent(event);
      this.status.update(s => ({
        ...(s ?? { active: true }),
        stage: 'await_plan',
        editPlanJson: event.editPlanJson,
        planDisplayJson: event.planDisplayJson,
        assistantReply: event.summary ?? s?.assistantReply,
        active: true
      }));
      if (event.summary) {
        this.setAssistantText(event.summary);
      }
      this.releaseBusyForHumanInput();
      return;
    }
    if (type === 'preview_ready' && event.candidateXml) {
      void this.editor.importBpmnXml(event.candidateXml);
      this.status.update(s => ({
        ...(s ?? { active: true }),
        stage: 'await_preview',
        candidateXml: event.candidateXml,
        active: true
      }));
      this.releaseBusyForHumanInput();
      return;
    }
    if (type === 'await_human') {
      this.status.update(s => ({
        ...(s ?? { active: true }),
        stage: event.stage,
        askMessage: event.askMessage,
        pluginHintJson: event.pluginHintJson,
        active: true
      }));
      this.releaseBusyForHumanInput();
      return;
    }
    if (type === 'done') {
      if (event.content) {
        this.setAssistantText(event.content);
      }
      this.status.update(s => ({ ...(s ?? { active: false }), stage: 'done', active: false }));
      this.busy.set(false);
      return;
    }
    if (type === 'error') {
      this.nzMessage.error(event.errorMessage ?? 'Agent 错误');
      this.status.update(s => ({
        ...(s ?? { active: false }),
        stage: 'error',
        active: false,
        errorMessage: event.errorMessage
      }));
      this.busy.set(false);
    }
  }

  /** SSE 在 await_plan / await_preview / await_ask 等人机闸门处不会结束，需主动释放 busy。 */
  private releaseBusyForHumanInput(): void {
    this.busy.set(false);
  }

  private attachContinuationStream(runId: string): void {
    this.streamAbort?.abort();
    this.streamAbort = this.agentApi.resumeRunStream(
      runId,
      (event, name) => this.handleEvent(event, name),
      err => {
        this.busy.set(false);
        this.nzMessage.error(err instanceof Error ? err.message : 'SSE 续推失败');
      },
      () => {
        this.busy.set(false);
      }
    );
  }

  private async buildContext(): Promise<{ bpmnXml: string; selectedElementId?: string }> {
    const process = this.editor.getBpmProcess();
    let bpmnXml = process?.bpmnXml ?? '';
    const modeler = this.editor.bpmnModeler;
    if (modeler) {
      try {
        const saved = await modeler.saveXML({ format: false });
        if (saved.xml) {
          bpmnXml = saved.xml;
        }
      } catch {
        /* keep server xml */
      }
    }
    const maxLen = 48_000;
    if (bpmnXml.length > maxLen) {
      bpmnXml = `${bpmnXml.slice(0, maxLen)}\n<!-- truncated -->`;
    }
    const selected = this.editor.getSelectedElementId();
    return { bpmnXml, selectedElementId: selected ?? undefined };
  }

  private startAssistantBubble(): void {
    this.messages.update(list => {
      const next = [...list, { role: 'assistant' as const, text: '', thinking: [] }];
      this.assistantIdx = next.length - 1;
      return next;
    });
  }

  private appendThinking(line: string): void {
    if (this.assistantIdx < 0) {
      this.startAssistantBubble();
    }
    this.messages.update(list => {
      const next = [...list];
      const msg = next[this.assistantIdx];
      if (msg) {
        msg.thinking = [...msg.thinking, line];
      }
      return next;
    });
  }

  private appendAssistantText(delta: string): void {
    if (this.assistantIdx < 0) {
      this.startAssistantBubble();
    }
    this.messages.update(list => {
      const next = [...list];
      const msg = next[this.assistantIdx];
      if (msg) {
        msg.text += delta;
      }
      return next;
    });
  }

  private syncPlanDisplay(status: DesignerAgentRunStatus): void {
    const display = resolvePlanDisplay(
      status.planDisplayJson,
      status.editPlanJson,
      status.assistantReply,
      this.componentProvider
    );
    this.planDisplay.set(display);
    this.planTechnicalJson.set(status.editPlanJson ?? '');
  }

  private syncPlanDisplayFromEvent(event: AgentStreamEvent): void {
    const display = resolvePlanDisplay(
      event.planDisplayJson,
      event.editPlanJson,
      event.summary,
      this.componentProvider
    );
    this.planDisplay.set(display);
    this.planTechnicalJson.set(event.editPlanJson ?? '');
  }

  private setAssistantText(text: string): void {
    if (this.assistantIdx < 0) {
      this.startAssistantBubble();
    }
    this.messages.update(list => {
      const next = [...list];
      const msg = next[this.assistantIdx];
      if (msg) {
        msg.text = text;
      }
      return next;
    });
  }
}
