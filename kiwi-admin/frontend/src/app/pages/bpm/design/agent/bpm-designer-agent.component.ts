import { AfterViewChecked, AfterViewInit, Component, computed, DestroyRef, effect, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { DesignerHarnessService, DesignerHarnessStatus } from '@services/ai-chat/designer-harness.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { BpmEditorToken } from '../editor/bpm-editor-token';

interface ChatMsg {
  role: 'user' | 'assistant';
  text: string;
}

@Component({
  selector: 'bpm-designer-agent',
  standalone: true,
  imports: [FormsModule, NzButtonModule, NzInputModule, NzSpinModule, NzTagModule],
  templateUrl: './bpm-designer-agent.component.html',
  styleUrl: './bpm-designer-agent.component.scss'
})
export class BpmDesignerAgentComponent implements AfterViewChecked, AfterViewInit {
  @ViewChild('messagesEnd') private messagesEnd?: ElementRef<HTMLElement>;
  @ViewChild('chatInput') private chatInput?: ElementRef<HTMLTextAreaElement>;

  private scrollPending = false;
  private lastAppliedXml = '';
  private loadedProcessId: string | null = null;
  private readonly editor = inject(BpmEditorToken);
  private readonly agentApi = inject(DesignerHarnessService);
  private readonly nzMessage = inject(NzMessageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly busy = signal(false);
  readonly inputText = signal('');
  readonly messages = signal<ChatMsg[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly agentEnabled = signal(true);
  readonly agentEnabledLoaded = signal(false);

  readonly bpmProcessId = computed(() => {
    const process = this.editor.getBpmProcess();
    return this.editor.getBpmnId() || process?.id || '';
  });

  readonly hasOpenSession = computed(() => this.messages().length > 0);
  readonly inputLocked = computed(() => this.busy() || !this.agentEnabled() || !this.bpmProcessId());
  readonly canSubmitSend = computed(() => !!this.inputText().trim() && !this.inputLocked());
  readonly stageLabel = computed(() => {
    if (this.busy()) {
      return '处理中';
    }
    if (this.messages().length) {
      return '对话中';
    }
    return '';
  });
  readonly inputPlaceholder = computed(() => {
    if (!this.bpmProcessId()) {
      return '请先打开一条流程…';
    }
    if (!this.agentEnabled()) {
      return 'Agent 未启用';
    }
    return '描述要如何修改流程…';
  });

  constructor() {
    void firstValueFrom(this.agentApi.fetchConfig())
      .then(cfg => {
        this.agentEnabled.set(cfg.enabled);
        this.agentEnabledLoaded.set(true);
      })
      .catch(() => {
        this.agentEnabled.set(false);
        this.agentEnabledLoaded.set(true);
      });

    effect(() => {
      const id = this.bpmProcessId();
      if (!id) {
        this.messages.set([]);
        this.errorMessage.set(null);
        this.loadedProcessId = null;
        return;
      }
      if (id !== this.loadedProcessId) {
        this.messages.set([]);
        this.loadedProcessId = id;
      }
      void this.loadPersistedSession(id);
    });

    effect(() => {
      this.busy();
      this.inputLocked();
      if (!this.inputLocked()) {
        this.queueFocusChatInput();
      }
    });

    this.destroyRef.onDestroy(() => {
      this.loadedProcessId = null;
    });
  }

  ngAfterViewInit(): void {
    this.queueFocusChatInput();
  }

  ngAfterViewChecked(): void {
    if (!this.scrollPending) {
      return;
    }
    this.scrollPending = false;
    this.messagesEnd?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }

  onInputKeydown(ev: KeyboardEvent): void {
    if (ev.key === 'Enter' && !ev.shiftKey) {
      ev.preventDefault();
      if (this.canSubmitSend()) {
        void this.send();
      }
    }
  }

  clearSession(): void {
    const processId = this.bpmProcessId();
    if (!processId || this.busy()) {
      return;
    }
    void firstValueFrom(this.agentApi.clear(processId))
      .then(status => {
        this.lastAppliedXml = '';
        void this.applyServerStatus(status);
        this.nzMessage.success('已清空 Agent 会话');
      })
      .catch(err => {
        this.nzMessage.error(err instanceof Error ? err.message : '清空会话失败');
      });
  }

  async send(): Promise<void> {
    const processId = this.bpmProcessId();
    const text = this.inputText().trim();
    if (!processId || !text || this.inputLocked()) {
      return;
    }
    if (!this.agentEnabled()) {
      this.nzMessage.warning('BPM 设计器 Agent 未启用，请联系管理员配置 kiwi.ai.enabled');
      return;
    }
    this.messages.update(list => [...list, { role: 'user', text }]);
    this.inputText.set('');
    this.busy.set(true);
    this.errorMessage.set(null);
    this.requestScrollToBottom();
    try {
      const canvasBpmnXml = await this.editor.exportBpmnXml();
      const status = await firstValueFrom(
        this.agentApi.postTurn({
          targetProcessId: processId,
          message: text,
          canvasBpmnXml,
          selectedElementId: this.editor.getSelectedElementId()
        })
      );
      await this.applyServerStatus(status);
    } catch (err) {
      this.busy.set(false);
      this.nzMessage.error(err instanceof Error ? err.message : '发送失败');
      await this.loadPersistedSession(processId);
    }
  }

  private async loadPersistedSession(processId: string): Promise<void> {
    try {
      const status = await firstValueFrom(this.agentApi.getSession(processId));
      if (processId !== this.bpmProcessId()) {
        return;
      }
      await this.applyServerStatus(status);
    } catch {
      /* 忽略加载失败，用户仍可新发消息 */
    }
  }

  private async applyServerStatus(status: DesignerHarnessStatus): Promise<void> {
    this.messages.set(
      (status.messages ?? []).map(row => ({
        role: row.role === 'user' ? ('user' as const) : ('assistant' as const),
        text: row.text ?? ''
      }))
    );
    this.busy.set(!!status.running);
    this.errorMessage.set(status.errorMessage ?? null);
    this.requestScrollToBottom();
    if (!this.editor.getBpmProcess()) {
      return;
    }
    const xml = status.bpmnXml?.trim() ?? '';
    const current = (this.editor.getBpmProcess()?.bpmnXml ?? '').trim();
    if (!xml || xml === this.lastAppliedXml || xml === current) {
      this.lastAppliedXml = xml || this.lastAppliedXml;
      return;
    }
    try {
      await this.editor.importBpmnXml(xml);
      this.lastAppliedXml = xml;
    } catch {
      this.nzMessage.warning('已保存的 BPMN 导入画布失败');
    }
  }

  private requestScrollToBottom(): void {
    this.scrollPending = true;
  }

  private queueFocusChatInput(): void {
    queueMicrotask(() => {
      const el = this.chatInput?.nativeElement;
      if (!el || el.disabled) {
        return;
      }
      el.focus({ preventScroll: true });
    });
  }
}
