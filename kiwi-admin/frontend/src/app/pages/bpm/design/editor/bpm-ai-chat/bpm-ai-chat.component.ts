import { Component, inject, input } from '@angular/core';
import type { AiChatMessage } from '@services/ai-chat/ai-chat.service';
import { ChatComponent } from '@shared/components/chat/chat.component';
import type { AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';

import {
  createBpmDesignerAssistantHandlers,
  type BpmDesignerAssistantDeps,
} from '../../assistant/bpm-designer-assistant.handlers';
import { BpmEditorAppendService } from '../../service/bpm-editor-append.service';
import { BpmDesignerToolbarService } from '../../toolbar/bpm-designer-toolbar.service';
import type { BpmDesignerToolbarContext } from '../../toolbar/bpm-designer-toolbar.types';
import { BpmEditorToken } from '../bpm-editor-token';

@Component({
  selector: 'bpm-ai-chat',
  standalone: true,
  imports: [ChatComponent],
  templateUrl: './bpm-ai-chat.component.html',
  styleUrl: './bpm-ai-chat.component.scss',
})
export class BpmAiChatComponent {
  private readonly editor = inject(BpmEditorToken);
  private readonly append = inject(BpmEditorAppendService);
  private readonly toolbarService = inject(BpmDesignerToolbarService);

  readonly getToolbarContext = input.required<() => BpmDesignerToolbarContext | undefined>();

  private readonly assistantDeps: BpmDesignerAssistantDeps = {
    importBpmnXml: (xml) => this.editor.importBpmnXml(xml),
    appendComponentForAi: (componentId, sourceElementId) =>
      this.append.appendComponentForAi(componentId, sourceElementId),
    runToolbarCommand: (command, options) => this.runToolbarCommand(command, options),
  };

  readonly assistantHandlers: AssistantActionHandler[] = createBpmDesignerAssistantHandlers(
    this.assistantDeps,
  );

  enrichDesignerMessages = (messages: AiChatMessage[]): Promise<AiChatMessage[]> => {
    return this.buildDesignerContextMessage().then((ctx) => [ctx, ...messages]);
  };

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
      '你正在 Kiwi BPM 流程设计器中协助用户。请结合下列上下文回答；若需改图，请通过 assistant_designer_* 工具登记客户端动作。',
      `processId: ${processId}`,
      process?.name ? `processName: ${process.name}` : '',
      selectedId ? `selectedElementId: ${selectedId}` : 'selectedElementId: （无单选元素）',
      `可用 toolbar 命令: ${this.toolbarService.listAiCommandIds().join(', ')}`,
      'appendComponent 参数: componentId（组件库 id）、sourceElementId（可选）',
      '当前 BPMN XML:',
      '```xml',
      xmlBlock || '（空）',
      '```',
    ].filter(Boolean);
    return { role: 'system', content: lines.join('\n') };
  }
}
