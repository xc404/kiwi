import { Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

import {
  AiAuthoringStatus,
  AiAuthoringTaskInfo,
  AiWorkflowAuthoringService
} from '@services/ai-chat/ai-workflow-authoring.service';
import type { AiChatMessage } from '@services/ai-chat/ai-chat.service';
import type { AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';
import { ChatComponent } from '@shared/components/chat/chat.component';

import { ComponentProvider } from '../../../flow-elements/component-provider';
import { createBpmDesignerAssistantHandlers, type BpmDesignerAssistantDeps } from '../../assistant/bpm-designer-assistant.handlers';
import { BpmEditorAppendService } from '../../service/bpm-editor-append.service';
import { BpmDesignerToolbarService } from '../../toolbar/bpm-designer-toolbar.service';
import type { BpmDesignerToolbarContext } from '../../toolbar/bpm-designer-toolbar.types';
import { BpmEditorToken } from '../bpm-editor-token';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

const TaskPreview = 'UserTask_Preview';
const TaskInstall = 'UserTask_Install';
const TaskAsk = 'UserTask_Ask';

const StageLabels: Record<string, string> = {
  extract: '抽词',
  catalog: '检索组件目录',
  generate: '生成候选流程',
  validate: '校验',
  repair: '修复中',
  await_preview: '等待预览确认',
  await_install: '等待安装插件',
  await_ask: '等待补充说明',
  save: '保存中',
  done: '已完成'
};

@Component({
  selector: 'bpm-ai-chat',
  standalone: true,
  imports: [ChatComponent, FormsModule, NzButtonModule, NzIconModule, NzInputModule, NzTagModule, NzTypographyModule],
  templateUrl: './bpm-ai-chat.component.html',
  styleUrl: './bpm-ai-chat.component.scss'
})
export class BpmAiChatComponent {
  private readonly editor = inject(BpmEditorToken);
  private readonly append = inject(BpmEditorAppendService);
  private readonly componentProvider = inject(ComponentProvider);
  private readonly toolbarService = inject(BpmDesignerToolbarService);
  private readonly authoringApi = inject(AiWorkflowAuthoringService);
  private readonly nzMessage = inject(NzMessageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly getToolbarContext = input.required<() => BpmDesignerToolbarContext | undefined>();

  readonly bpmProcessId = computed(() => {
    const process = this.editor.getBpmProcess();
    return this.editor.getBpmnId() || process?.id || '';
  });

  readonly authoringStatus = signal<AiAuthoringStatus | null>(null);
  readonly authoringBusy = signal(false);
  readonly askAnswer = signal('');
  readonly panelOpen = signal(true);

  private lastImportedPreviewXml = '';
  /** 首次导入预览前的画布 XML，拒绝预览时恢复 */
  private xmlBeforePreview = '';

  private readonly assistantDeps: BpmDesignerAssistantDeps = {
    importBpmnXmlAndSave: xml => this.editor.importBpmnXmlAndSave(xml),
    importBpmnXml: xml => this.editor.importBpmnXml(xml),
    applyMatchedComponent: (componentId, sourceElementId) => this.append.appendComponentForAi(componentId, sourceElementId),
    runToolbarCommand: (command, options) => this.runToolbarCommand(command, options)
  };

  readonly assistantHandlers: AssistantActionHandler[] = createBpmDesignerAssistantHandlers(this.assistantDeps);

  readonly showAuthoringPanel = computed(() => {
    const s = this.authoringStatus();
    return !!s?.active || s?.stage === 'done';
  });

  readonly stageLabel = computed(() => {
    const stage = this.authoringStatus()?.stage;
    if (!stage) {
      return '未知';
    }
    return StageLabels[stage] ?? stage;
  });

  readonly previewTask = computed(() => this.findTask(TaskPreview));
  readonly installTask = computed(() => this.findTask(TaskInstall));
  readonly askTask = computed(() => this.findTask(TaskAsk));

  enrichDesignerMessages = (messages: AiChatMessage[]): Promise<AiChatMessage[]> => {
    return this.buildDesignerContextMessage().then(ctx => [ctx, ...messages]);
  };

  constructor() {
    effect(() => {
      const processId = this.bpmProcessId();
      if (!processId) {
        this.authoringStatus.set(null);
        this.lastImportedPreviewXml = '';
        this.xmlBeforePreview = '';
        return;
      }
      this.refreshAuthoringStatus();
    });
  }

  onChatTurnCompleted(): void {
    this.refreshAuthoringStatus();
  }

  refreshAuthoringStatus(): void {
    const processId = this.bpmProcessId();
    if (!processId) {
      return;
    }
    this.authoringApi
      .statusByTarget(processId)
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(status => {
        if (!status) {
          return;
        }
        void this.applyAuthoringStatus(status);
      });
  }

  confirmPreview(): void {
    const task = this.previewTask();
    const xml = this.authoringStatus()?.candidateXml?.trim() ?? '';
    this.completeHumanTask(task, { previewConfirmed: true }, async () => {
      if (xml) {
        await this.editor.importBpmnXml(xml);
      }
      this.xmlBeforePreview = '';
      this.lastImportedPreviewXml = '';
      this.nzMessage.success('已确认并保存到当前流程');
    });
  }

  declinePreview(): void {
    const restoreXml = this.xmlBeforePreview;
    this.completeHumanTask(this.previewTask(), { previewConfirmed: false }, async () => {
      if (restoreXml) {
        try {
          await this.editor.importBpmnXml(restoreXml);
        } catch {
          /* 忽略恢复失败 */
        }
      }
      this.xmlBeforePreview = '';
      this.lastImportedPreviewXml = '';
      this.nzMessage.info('已拒绝预览，将重新生成');
    });
  }

  confirmInstall(): void {
    this.completeHumanTask(this.installTask(), { installAccepted: true }, () => {
      this.nzMessage.success('已确认安装，继续校验');
    });
  }

  declineInstall(): void {
    this.completeHumanTask(this.installTask(), { installAccepted: false }, () => {
      this.nzMessage.info('已拒绝安装，将重新生成');
    });
  }

  submitAskAnswer(): void {
    const answer = this.askAnswer().trim();
    if (!answer) {
      this.nzMessage.warning('请先填写补充说明');
      return;
    }
    this.completeHumanTask(this.askTask(), { userAnswer: answer }, () => {
      this.askAnswer.set('');
      this.nzMessage.success('已提交补充说明');
    });
  }

  dismissDonePanel(): void {
    this.authoringStatus.set(null);
    this.lastImportedPreviewXml = '';
    this.xmlBeforePreview = '';
  }

  private completeHumanTask(
    task: AiAuthoringTaskInfo | undefined,
    variables: Record<string, unknown>,
    onOk?: () => void | Promise<void>
  ): void {
    if (!task?.id) {
      this.nzMessage.warning('当前没有待办任务');
      return;
    }
    if (this.authoringBusy()) {
      return;
    }
    this.authoringBusy.set(true);
    this.authoringApi
      .completeTask(task.id, variables)
      .pipe(
        finalize(() => this.authoringBusy.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: status => {
          void (async () => {
            await onOk?.();
            await this.applyAuthoringStatus(status);
          })();
        },
        error: (err: { message?: string }) => {
          this.nzMessage.error(err?.message ?? '完成任务失败');
        }
      });
  }

  private async applyAuthoringStatus(status: AiAuthoringStatus): Promise<void> {
    this.authoringStatus.set(status);
    if (!status.active && status.stage !== 'done') {
      return;
    }
    const xml = status.candidateXml?.trim() ?? '';
    if (status.stage === 'await_preview' && xml && xml !== this.lastImportedPreviewXml) {
      try {
        if (!this.xmlBeforePreview) {
          this.xmlBeforePreview = this.editor.getBpmProcess()?.bpmnXml ?? '';
        }
        await this.editor.importBpmnXml(xml);
        this.lastImportedPreviewXml = xml;
      } catch {
        this.nzMessage.warning('候选流程预览导入失败，可稍后重试');
      }
    }
  }

  private findTask(definitionKey: string): AiAuthoringTaskInfo | undefined {
    return this.authoringStatus()?.tasks?.find(t => t.taskDefinitionKey === definitionKey);
  }

  private runToolbarCommand(command: string, options?: Record<string, unknown>): void {
    const ctx = this.getToolbarContext()();
    if (!ctx) {
      throw new Error('工具栏未就绪');
    }
    this.toolbarService.run(command, ctx, options);
  }

  private async buildDesignerContextMessage(): Promise<AiChatMessage> {
    const process = this.editor.getBpmProcess();
    const processId = this.editor.getBpmnId() || process?.id || '';
    let xml = process?.bpmnXml ?? '';
    const modeler = this.editor.bpmnModeler;
    if (modeler) {
      try {
        const saved = await modeler.saveXML({ format: false });
        if (saved.xml) {
          xml = saved.xml;
        }
      } catch {
        /* 保留流程定义上的 XML */
      }
    }
    const maxLen = 48_000;
    let xmlBlock = xml;
    if (xmlBlock.length > maxLen) {
      xmlBlock = `${xmlBlock.slice(0, maxLen)}\n<!-- …已截断，完整图请保存后重试… -->`;
    }
    const selectedId = this.editor.getSelectedElementId();
    const lines = [
      '你正在 Kiwi BPM 流程设计器中协助用户。',
      '加组件：assistant_designer_match_component(componentId)；画布追加与锚点由前端处理。',
      '改图分工：仅下列意图用 assistant_designer_toolbar（undo/redo/zoom/copy/paste/removeSelection/find/save/deploy/start/export/saveAsComponent 等）；其余一律 assistant_designer_bpmn_xml(完整 definitions)，前端会自动 import 并保存到当前流程。',
      '须走 bpmn_xml 的示例：改节点参数/复制它流程配置/增删改连线或节点/移除或删除组件/批量改名；删除：从当前 XML 去掉目标 serviceTask 与相关 sequenceFlow、BPMNDI 后 assistant_designer_bpmn_xml；复制：bpmPd_get → 合并 extensionElements → assistant_designer_bpmn_xml；仅有流程名时用 bpmPd_aiPage 查 id。',
      '禁止未调用 assistant_designer_bpmn_xml 却声称已修改或已保存。',
      '场景级「写工作流」由服务端编排流程处理；用户在设计器侧栏完成预览确认/插件安装/追问，无需再让用户调 REST。',
      `processId: ${processId}`,
      process?.name ? `processName: ${process.name}` : '',
      selectedId ? `selectedElementId: ${selectedId}` : 'selectedElementId: （无单选元素；追加组件建议 sourceElementId=StartEvent_1）',
      `可用 toolbar 命令: ${this.toolbarService.listAiCommandIds().join(', ')}`,
      'matchComponent：assistant_designer_match_component 的 componentId 必须来自下列组件库列表；若无法确定追加锚点，请让用户在画布选中节点或回复元素 id',
      this.buildComponentCatalogLine(),
      '当前 BPMN XML:',
      '```xml',
      xmlBlock || '（空）',
      '```'
    ].filter(Boolean);
    return { role: 'system', content: lines.join('\n') };
  }

  private buildComponentCatalogLine(): string {
    const list = this.componentProvider.components();
    if (!list.length) {
      return '组件库 componentId|name: （尚未加载）';
    }
    const max = 60;
    const slice = list.slice(0, max);
    const catalog = slice.map(c => `${c.id}|${c.name}`).join('; ');
    const suffix = list.length > max ? `; …共 ${list.length} 个` : '';
    return `组件库 componentId|name: ${catalog}${suffix}`;
  }
}
