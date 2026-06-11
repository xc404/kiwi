import { Component, computed, inject, input } from '@angular/core';

import type { AiChatMessage } from '@services/ai-chat/ai-chat.service';
import type { AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';
import { ChatComponent } from '@shared/components/chat/chat.component';

import { ComponentProvider } from '../../../flow-elements/component-provider';
import { createBpmDesignerAssistantHandlers, type BpmDesignerAssistantDeps } from '../../assistant/bpm-designer-assistant.handlers';
import { BpmEditorAppendService } from '../../service/bpm-editor-append.service';
import { BpmDesignerToolbarService } from '../../toolbar/bpm-designer-toolbar.service';
import type { BpmDesignerToolbarContext } from '../../toolbar/bpm-designer-toolbar.types';
import { BpmEditorToken } from '../bpm-editor-token';

@Component({
  selector: 'bpm-ai-chat',
  standalone: true,
  imports: [ChatComponent],
  templateUrl: './bpm-ai-chat.component.html',
  styleUrl: './bpm-ai-chat.component.scss'
})
export class BpmAiChatComponent {
  private readonly editor = inject(BpmEditorToken);
  private readonly append = inject(BpmEditorAppendService);
  private readonly componentProvider = inject(ComponentProvider);
  private readonly toolbarService = inject(BpmDesignerToolbarService);

  readonly getToolbarContext = input.required<() => BpmDesignerToolbarContext | undefined>();

  readonly bpmProcessId = computed(() => {
    const process = this.editor.getBpmProcess();
    return this.editor.getBpmnId() || process?.id || '';
  });

  private readonly assistantDeps: BpmDesignerAssistantDeps = {
    importBpmnXmlAndSave: xml => this.editor.importBpmnXmlAndSave(xml),
    applyMatchedComponent: (componentId, sourceElementId) => this.append.appendComponentForAi(componentId, sourceElementId),
    runToolbarCommand: (command, options) => this.runToolbarCommand(command, options)
  };

  readonly assistantHandlers: AssistantActionHandler[] = createBpmDesignerAssistantHandlers(this.assistantDeps);

  enrichDesignerMessages = (messages: AiChatMessage[]): Promise<AiChatMessage[]> => {
    return this.buildDesignerContextMessage().then(ctx => [ctx, ...messages]);
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
      '你正在 Kiwi BPM 流程设计器中协助用户。',
      '加组件：assistant_designer_match_component(componentId)；画布追加与锚点由前端处理。',
      '改图分工：仅下列意图用 assistant_designer_toolbar（undo/redo/zoom/copy/paste/removeSelection/find/save/deploy/start/export/saveAsComponent 等）；其余一律 assistant_designer_bpmn_xml(完整 definitions)，前端会自动 import 并保存到当前流程。',
      '须走 bpmn_xml 的示例：改节点参数/复制它流程配置/增删改连线或节点/移除或删除组件/批量改名；删除：从当前 XML 去掉目标 serviceTask 与相关 sequenceFlow、BPMNDI 后 assistant_designer_bpmn_xml；复制：bpmPd_get → 合并 extensionElements → assistant_designer_bpmn_xml；仅有流程名时用 bpmPd_aiPage 查 id。',
      '禁止未调用 assistant_designer_bpmn_xml 却声称已修改或已保存。',
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
