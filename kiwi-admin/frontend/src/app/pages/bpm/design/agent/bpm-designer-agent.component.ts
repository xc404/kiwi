import { Component, computed, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

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

const HumanGateStages = new Set(['await_plan', 'await_preview', 'await_ask', 'await_install']);

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

  readonly busy = signal(false);
  readonly inputText = signal('');
  readonly askText = signal('');
  readonly status = signal<DesignerAgentRunStatus | null>(null);
  readonly messages = signal<ChatMsg[]>([]);
  readonly planDisplay = signal<PlanDisplayView | null>(null);
  readonly planTechnicalJson = signal('');
  readonly stepKindIcon = stepKindIcon;

  private streamAbort: AbortController | null = null;
  private streamConnected = false;
  private assistantIdx = -1;
  private previewXmlApplied: string | null = null;
  private runBaselineXml: string | null = null;
  private previewCanvasDirty = signal(false);

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
  readonly previewEdited = computed(() => this.previewCanvasDirty());
  readonly inputLocked = computed(
    () => this.busy() || this.awaitPlan() || this.awaitPreview() || this.awaitAsk()
  );

  constructor() {
    effect(() => {
      const id = this.bpmProcessId();
      if (!id) {
        this.status.set(null);
        return;
      }
      if (this.streamConnected) {
        return;
      }
      this.agentApi
        .statusByTarget(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(s => {
          if (!this.streamConnected) {
            this.applyServerStatus(s);
          }
        });
    });
  }

  send(): void {
    const text = this.inputText().trim();
    const processId = this.bpmProcessId();
    if (!text || !processId || this.inputLocked()) {
      return;
    }
    this.messages.update(list => [...list, { role: 'user', text, thinking: [] }]);
    this.inputText.set('');
    this.busy.set(true);
    this.closeStream();
    void this.buildContext().then(async ctx => {
      try {
        const created = await firstValueFrom(
          this.agentApi.createRun({
            scenario: text,
            targetProcessId: processId,
            selectedElementId: ctx.selectedElementId,
            baseBpmnXml: ctx.bpmnXml
          })
        );
        this.previewXmlApplied = null;
        this.previewCanvasDirty.set(false);
        this.runBaselineXml = ctx.bpmnXml.trim() || null;
        this.editor.setAgentPreviewActive(false);
        this.startAssistantBubble();
        this.applyServerStatus(created);
        this.streamConnected = true;
        this.streamAbort = this.agentApi.openEventStream(
          created.runId!,
          (event, name) => void this.onStreamEvent(event, name),
          err => {
            this.streamConnected = false;
            this.busy.set(false);
            this.nzMessage.error(err instanceof Error ? err.message : '事件流失败');
          },
          () => {
            this.streamConnected = false;
            void this.refreshStatus(created.runId);
          }
        );
      } catch (err) {
        this.busy.set(false);
        this.nzMessage.error(err instanceof Error ? err.message : '启动 Agent 失败');
      }
    });
  }

  confirmPlan(confirmed: boolean): void {
    const runId = this.status()?.runId;
    if (!runId || this.busy()) {
      return;
    }
    void this.runHumanAction(runId, async () => {
      await this.assertStage(runId, 'await_plan', '计划确认');
      const canvasBpmnXml = confirmed ? await this.captureCanvasForAction() : undefined;
      await firstValueFrom(
        this.agentApi.submitAction(runId, { type: 'confirm_plan', confirmed, canvasBpmnXml })
      );
    });
  }

  confirmPreview(confirmed: boolean): void {
    const runId = this.status()?.runId;
    if (!runId || this.busy()) {
      return;
    }
    void this.runHumanAction(runId, async () => {
      await this.assertStage(runId, 'await_preview', '预览确认');
      const hadManualEdits = this.awaitPreview() ? await this.isPreviewCanvasDirty() : false;
      const canvasBpmnXml = confirmed ? await this.captureCanvasForAction() : undefined;
      const result = await firstValueFrom(
        this.agentApi.submitAction(runId, {
          type: 'confirm_preview',
          confirmed,
          canvasBpmnXml: confirmed ? canvasBpmnXml : undefined
        })
      );
      this.applyServerStatus(result);
      this.editor.setAgentPreviewActive(false);
      this.previewCanvasDirty.set(false);
      if (confirmed) {
        this.previewXmlApplied = null;
        this.runBaselineXml = canvasBpmnXml?.trim() ?? null;
        if (hadManualEdits) {
          this.nzMessage.success('已保存流程（含您在预览期间的手动修改）');
        } else {
          this.nzMessage.success('已保存流程');
        }
      } else {
        await this.editor.rejectAgentPreview();
        this.previewXmlApplied = null;
        this.previewCanvasDirty.set(false);
        this.nzMessage.info('已回退预览');
      }
    });
  }

  submitAsk(): void {
    const runId = this.status()?.runId;
    const answer = this.askText().trim();
    if (!runId || !answer || this.busy()) {
      return;
    }
    void this.runHumanAction(runId, async () => {
      await this.assertStage(runId, 'await_ask', '追问');
      const canvasBpmnXml = await this.captureCanvasForAction();
      await firstValueFrom(
        this.agentApi.submitAction(runId, { type: 'answer', userAnswer: answer, canvasBpmnXml })
      );
      this.askText.set('');
      this.runBaselineXml = canvasBpmnXml?.trim() ?? this.runBaselineXml;
    });
  }

  onInputKeydown(ev: KeyboardEvent): void {
    if (ev.key === 'Enter' && !ev.shiftKey) {
      ev.preventDefault();
      this.send();
    }
  }

  /** 事件流只展示日志；阶段变化一律 GET /runs/{id} */
  private async onStreamEvent(event: AgentStreamEvent, eventName: string): Promise<void> {
    const type = event.type ?? eventName;
    if (type === 'stage') {
      this.appendThinking(`${event.label ?? event.stage}: ${event.detail ?? ''}`);
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
      if (event.summary) {
        this.setAssistantText(event.summary);
      }
      await this.refreshStatus(event.runId);
      this.busy.set(false);
      return;
    }
    if (type === 'preview_ready') {
      await this.refreshStatus(event.runId);
      this.busy.set(false);
      return;
    }
    if (type === 'await_human') {
      await this.refreshStatus(event.runId);
      this.busy.set(false);
      return;
    }
    if (type === 'done') {
      if (event.content) {
        this.setAssistantText(event.content);
      }
      await this.refreshStatus(event.runId);
      this.busy.set(false);
      return;
    }
    if (type === 'error') {
      this.nzMessage.error(event.errorMessage ?? 'Agent 错误');
      await this.refreshStatus(event.runId);
      this.busy.set(false);
    }
  }

  private async assertStage(runId: string, expected: string, label: string): Promise<void> {
    const current = await firstValueFrom(this.agentApi.statusByRunId(runId));
    this.applyServerStatus(current);
    if (current.stage !== expected) {
      throw new Error(
        `当前不在${label}阶段（${StageLabels[current.stage ?? ''] ?? current.stage ?? '未知'}）`
      );
    }
  }

  private async runHumanAction(runId: string, action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
      await this.refreshStatus(runId);
      this.syncBusyAndStreamAfterAction(runId);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '操作失败';
      if (this.isSessionExpiredError(msg)) {
        this.handleSessionExpired();
        this.nzMessage.warning('Agent 会话已失效，请重新发送指令');
      } else {
        this.nzMessage.error(msg);
        await this.refreshStatus(runId).catch(() => undefined);
      }
      this.busy.set(false);
    }
  }

  /** Graph 继续跑时需要事件流；闸门阶段只依赖 GET 状态 */
  private syncBusyAndStreamAfterAction(runId: string): void {
    const current = this.status();
    const stage = current?.stage ?? '';
    if (HumanGateStages.has(stage)) {
      this.busy.set(false);
      return;
    }
    if (current?.active) {
      this.ensureEventStream(runId);
      this.busy.set(true);
      return;
    }
    this.busy.set(false);
  }

  private ensureEventStream(runId: string): void {
    if (this.streamConnected) {
      return;
    }
    this.streamConnected = true;
    this.streamAbort = this.agentApi.openEventStream(
      runId,
      (event, name) => void this.onStreamEvent(event, name),
      err => {
        this.streamConnected = false;
        this.busy.set(false);
        this.nzMessage.error(err instanceof Error ? err.message : '事件流失败');
      },
      () => {
        this.streamConnected = false;
        void this.refreshStatus(runId);
      }
    );
  }

  private isSessionExpiredError(message: string): boolean {
    return /run 不存在|run 已结束|会话已失效|NOT_FOUND|GONE|404|410/i.test(message);
  }

  private handleSessionExpired(): void {
    this.closeStream();
    this.status.set(null);
    this.editor.setAgentPreviewActive(false);
    this.previewXmlApplied = null;
    this.previewCanvasDirty.set(false);
    this.runBaselineXml = null;
  }

  private async refreshStatus(runId?: string): Promise<void> {
    const id = runId ?? this.status()?.runId;
    if (!id) {
      const targetId = this.bpmProcessId();
      if (!targetId) {
        return;
      }
      const s = await firstValueFrom(this.agentApi.statusByTarget(targetId));
      this.applyServerStatus(s);
      return;
    }
    try {
      const s = await firstValueFrom(this.agentApi.statusByRunId(id));
      this.applyServerStatus(s);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      if (!this.isSessionExpiredError(msg)) {
        throw err;
      }
      const targetId = this.bpmProcessId();
      if (targetId) {
        try {
          const byTarget = await firstValueFrom(this.agentApi.statusByTarget(targetId));
          if (byTarget.runId && byTarget.runId !== id) {
            this.handleSessionExpired();
            return;
          }
          if (byTarget.runId || byTarget.stage || byTarget.active) {
            this.applyServerStatus(byTarget);
            return;
          }
        } catch {
          /* fall through */
        }
      }
      this.handleSessionExpired();
    }
  }

  private applyServerStatus(incoming: DesignerAgentRunStatus): void {
    if (!incoming.runId && !incoming.stage && !incoming.active) {
      this.status.set(null);
      return;
    }
    this.status.set(incoming);
    this.syncPlanDisplay(incoming);
    if (incoming.stage === 'await_preview' && incoming.candidateXml) {
      void this.applyPreviewXml(incoming.candidateXml).then(applied => {
        if (applied && HumanGateStages.has(incoming.stage ?? '')) {
          this.nzMessage.info('已在画布加载预览，请查看流程图并确认是否保存');
        }
      });
    } else if (incoming.stage !== 'await_preview') {
      this.editor.setAgentPreviewActive(false);
    }
    if (HumanGateStages.has(incoming.stage ?? '')) {
      this.busy.set(false);
    }
  }

  private applyPreviewXml(xml: string): Promise<boolean> {
    const trimmed = xml.trim();
    if (!trimmed || trimmed === this.previewXmlApplied) {
      return Promise.resolve(false);
    }
    return this.editor
      .captureCurrentBpmnXml()
      .then(current => {
        const baseline = this.runBaselineXml;
        if (baseline && current !== baseline && current !== trimmed) {
          this.nzMessage.warning('检测到画布有手动修改，加载预览将暂时覆盖；确认保存时以当前画布为准');
        }
        return this.editor.importBpmnXmlForPreview(trimmed);
      })
      .then(() => {
        this.previewXmlApplied = trimmed;
        this.previewCanvasDirty.set(false);
        this.watchPreviewCanvasEdits();
        return true;
      })
      .catch(err => {
        this.nzMessage.error(err instanceof Error ? err.message : '预览导入失败');
        return false;
      });
  }

  refreshPreviewDirtyHint(): void {
    void this.refreshPreviewDirtyFlag();
  }

  private watchPreviewCanvasEdits(): void {
    if (!this.awaitPreview()) {
      return;
    }
    void this.refreshPreviewDirtyFlag();
  }

  private async refreshPreviewDirtyFlag(): Promise<void> {
    const dirty = await this.isPreviewCanvasDirty();
    this.previewCanvasDirty.set(dirty);
  }

  private async isPreviewCanvasDirty(): Promise<boolean> {
    if (!this.previewXmlApplied) {
      return false;
    }
    const current = (await this.editor.captureCurrentBpmnXml()).trim();
    return current !== this.previewXmlApplied;
  }

  private async captureCanvasForAction(): Promise<string | undefined> {
    const xml = (await this.editor.captureCurrentBpmnXml()).trim();
    if (!xml) {
      return undefined;
    }
    const maxLen = 48_000;
    if (xml.length > maxLen) {
      return `${xml.slice(0, maxLen)}\n<!-- truncated -->`;
    }
    return xml;
  }

  private closeStream(): void {
    this.streamAbort?.abort();
    this.streamAbort = null;
    this.streamConnected = false;
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
