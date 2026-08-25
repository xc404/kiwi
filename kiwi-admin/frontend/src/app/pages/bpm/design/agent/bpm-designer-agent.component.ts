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
import { planFeedbackFromText, previewFeedbackFromText } from './designer-agent-intent';

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
  await_follow_up: '可继续提问',
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
  private loadedProcessId: string | null = null;

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
  readonly awaitFollowUp = computed(() => this.status()?.stage === 'await_follow_up');
  readonly canFollowUp = computed(() => {
    const stage = this.status()?.stage;
    return stage === 'await_follow_up' || stage === 'error' || stage === 'done';
  });
  readonly hasOpenSession = computed(() => !!this.status()?.runId);
  readonly previewEdited = computed(() => this.previewCanvasDirty());
  readonly inputLocked = computed(() => this.busy());
  readonly inputPlaceholder = computed(() => {
    if (this.awaitAsk()) {
      return '补充说明您的需求…';
    }
    if (this.awaitPlan()) {
      return '输入修改意见，或短句「批准执行」…';
    }
    if (this.awaitPreview()) {
      return '输入调整意见，或短句「确认保存」…';
    }
    if (this.canFollowUp()) {
      return '继续描述要如何修改流程…';
    }
    return '描述要如何修改流程…';
  });

  constructor() {
    effect(() => {
      const id = this.bpmProcessId();
      const tab = this.editor.getLeftPanelTab();
      if (!id) {
        this.status.set(null);
        this.messages.set([]);
        this.loadedProcessId = null;
        return;
      }
      if (id !== this.loadedProcessId) {
        this.messages.set([]);
        this.loadedProcessId = id;
        this.closeStream();
      }
      if (tab !== 'agent' || this.streamConnected) {
        return;
      }
      void this.loadPersistedSession(id);
    });
  }

  private async loadPersistedSession(processId: string): Promise<void> {
    try {
      const s = await firstValueFrom(this.agentApi.statusByTarget(processId));
      if (processId !== this.bpmProcessId() || this.streamConnected) {
        return;
      }
      this.applyServerStatus(s, true);
      if (s.stage === 'await_preview' && s.candidateXml?.trim()) {
        void this.applyPreviewXml(s.candidateXml).then(applied => {
          if (applied) {
            this.nzMessage.info('已在画布加载预览，请查看流程图并确认是否保存');
          }
        });
      }
    } catch {
      /* 忽略加载失败，用户仍可新发消息 */
    }
  }

  send(): void {
    const text = this.inputText().trim();
    if (!text || this.busy()) {
      return;
    }
    if (this.awaitPlan()) {
      this.submitPlanFeedback(text);
      return;
    }
    if (this.awaitPreview()) {
      this.submitPreviewFeedback(text);
      return;
    }
    if (this.awaitAsk()) {
      this.submitAnswer(text);
      return;
    }
    const runId = this.status()?.runId;
    if (runId && this.canFollowUp()) {
      this.submitFollowUp(text);
      return;
    }
    const processId = this.bpmProcessId();
    if (!processId) {
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

  clearSession(): void {
    const processId = this.bpmProcessId();
    if (!processId || this.busy()) {
      return;
    }
    void firstValueFrom(this.agentApi.clearSession(processId))
      .then(() => {
        this.closeStream();
        this.status.set(null);
        this.messages.set([]);
        this.planDisplay.set(null);
        this.planTechnicalJson.set('');
        this.editor.setAgentPreviewActive(false);
        this.previewXmlApplied = null;
        this.previewCanvasDirty.set(false);
        this.runBaselineXml = null;
        this.nzMessage.success('已清空 Agent 会话');
      })
      .catch(err => {
        this.nzMessage.error(err instanceof Error ? err.message : '清空会话失败');
      });
  }

  private submitFollowUp(text: string): void {
    const runId = this.status()?.runId;
    if (!runId) {
      return;
    }
    this.messages.update(list => [...list, { role: 'user', text, thinking: [] }]);
    this.inputText.set('');
    this.busy.set(true);
    this.closeStream();
    void this.buildContext().then(async ctx => {
      try {
        const updated = await firstValueFrom(
          this.agentApi.followUp(runId, {
            message: text,
            selectedElementId: ctx.selectedElementId,
            canvasBpmnXml: ctx.bpmnXml
          })
        );
        this.previewXmlApplied = null;
        this.previewCanvasDirty.set(false);
        this.runBaselineXml = ctx.bpmnXml.trim() || null;
        this.editor.setAgentPreviewActive(false);
        this.startAssistantBubble();
        this.applyServerStatus(updated);
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
      } catch (err) {
        this.busy.set(false);
        this.nzMessage.error(err instanceof Error ? err.message : '续聊失败');
      }
    });
  }

  confirmPlan(confirmed: boolean, feedbackText?: string): void {
    const runId = this.status()?.runId;
    if (!runId || this.busy()) {
      return;
    }
    void this.runHumanAction(runId, async () => {
      await this.assertStage(runId, 'await_plan', '计划确认');
      const canvasBpmnXml = confirmed ? await this.captureCanvasForAction() : undefined;
      await firstValueFrom(
        this.agentApi.submitAction(runId, {
          type: 'confirm_plan',
          confirmed,
          canvasBpmnXml,
          feedbackText: confirmed ? undefined : feedbackText
        })
      );
    });
  }

  confirmPreview(confirmed: boolean, feedbackText?: string): void {
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
          canvasBpmnXml: confirmed ? canvasBpmnXml : undefined,
          feedbackText: confirmed ? undefined : feedbackText
        })
      );
      this.applyServerStatus(result);
      if (confirmed) {
        if (canvasBpmnXml?.trim()) {
          await this.editor.commitAgentPreviewSave(canvasBpmnXml);
        } else {
          this.editor.setAgentPreviewActive(false);
        }
        this.previewXmlApplied = null;
        this.previewCanvasDirty.set(false);
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

  private submitPlanFeedback(text: string): void {
    const { confirmed, feedbackText } = planFeedbackFromText(text);
    this.messages.update(list => [...list, { role: 'user', text, thinking: [] }]);
    this.inputText.set('');
    this.startAssistantBubble();
    this.confirmPlan(confirmed, feedbackText);
  }

  private submitPreviewFeedback(text: string): void {
    const { confirmed, feedbackText } = previewFeedbackFromText(text);
    this.messages.update(list => [...list, { role: 'user', text, thinking: [] }]);
    this.inputText.set('');
    this.startAssistantBubble();
    this.confirmPreview(confirmed, feedbackText);
  }

  private submitAnswer(answer: string): void {
    const runId = this.status()?.runId;
    if (!runId || this.busy()) {
      return;
    }
    this.messages.update(list => [...list, { role: 'user', text: answer, thinking: [] }]);
    this.inputText.set('');
    this.startAssistantBubble();
    void this.runHumanAction(runId, async () => {
      await this.assertStage(runId, 'await_ask', '追问');
      const canvasBpmnXml = await this.captureCanvasForAction();
      await firstValueFrom(
        this.agentApi.submitAction(runId, { type: 'answer', userAnswer: answer, canvasBpmnXml })
      );
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
      const st = this.status();
      if (st?.stage === 'await_preview' && st.candidateXml?.trim()) {
        const applied = await this.applyPreviewXml(st.candidateXml);
        if (applied) {
          this.nzMessage.info('已在画布加载预览，请查看流程图并确认是否保存');
        }
      }
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

  private applyServerStatus(incoming: DesignerAgentRunStatus, restoreMessages = false): void {
    if (!incoming.runId && !incoming.stage && !incoming.active && !incoming.messages?.length) {
      this.status.set(null);
      return;
    }
    this.status.set(incoming);
    if (restoreMessages) {
      this.hydrateChatFromServer(incoming);
    }
    this.syncPlanDisplay(incoming);
    if (incoming.stage === 'await_ask' && incoming.askMessage) {
      this.ensureAskMessageInChat(incoming.askMessage);
    }
    if (incoming.stage !== 'await_preview') {
      this.editor.setAgentPreviewActive(false);
    }
    if (HumanGateStages.has(incoming.stage ?? '')) {
      this.busy.set(false);
    } else if (incoming.stage === 'await_follow_up') {
      this.busy.set(false);
    }
  }

  private hydrateChatFromServer(incoming: DesignerAgentRunStatus): void {
    if (this.messages().length > 0) {
      return;
    }
    if (incoming.messages?.length) {
      this.restoreMessagesFromServer(incoming.messages);
      return;
    }
    this.rebuildMessagesFromStatus(incoming);
  }

  private rebuildMessagesFromStatus(status: DesignerAgentRunStatus): void {
    const built: ChatMsg[] = [];
    const assistantText = (status.askMessage ?? status.assistantReply ?? '').trim();
    if (assistantText) {
      built.push({ role: 'assistant', text: assistantText, thinking: [] });
    }
    if (built.length === 0) {
      return;
    }
    this.messages.set(built);
    this.assistantIdx = built.length - 1;
  }

  private restoreMessagesFromServer(
    rows: { role: string; text: string }[]
  ): void {
    this.messages.set(
      rows.map(row => ({
        role: row.role === 'user' ? ('user' as const) : ('assistant' as const),
        text: row.text ?? '',
        thinking: []
      }))
    );
    const msgs = this.messages();
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'assistant') {
        this.assistantIdx = i;
        break;
      }
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

  /** 刷新页面后恢复 await_ask 会话时，将追问补进聊天气泡 */
  private ensureAskMessageInChat(askMessage: string): void {
    const trimmed = askMessage.trim();
    if (!trimmed) {
      return;
    }
    const msgs = this.messages();
    if (msgs.some(m => m.role === 'assistant' && m.text.includes(trimmed))) {
      return;
    }
    this.messages.update(list => [...list, { role: 'assistant', text: trimmed, thinking: [] }]);
    this.assistantIdx = this.messages().length - 1;
  }
}
