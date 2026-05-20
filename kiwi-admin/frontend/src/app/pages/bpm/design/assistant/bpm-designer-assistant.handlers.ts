import type { AiClientAction } from '@services/ai-chat/ai-chat.service';
import type { AssistantActionContext, AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';

import type { BpmEditorToken } from '../editor/bpm-editor-token';
import type { BpmDesignerToolbarContext } from '../toolbar/bpm-designer-toolbar.types';

/** 处理 `type === 'bpmnXml'`：替换当前画布 BPMN。 */
export class BpmnXmlAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly getToolbar: () => BpmDesignerToolbarContext | undefined) {}

  supports(action: AiClientAction): boolean {
    const xml = action.params?.['xml'];
    return action.type === 'bpmnXml' && typeof xml === 'string' && !!xml.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    const toolbar = this.getToolbar();
    if (!toolbar) {
      ctx.nzMessage.error('工具栏未就绪，请稍后再试');
      return false;
    }
    const xml = String(action.params?.['xml'] ?? '');
    void toolbar.importBpmnXml(xml).catch(() => {
      /* 错误已在 import 内提示 */
    });
    ctx.nzMessage.info('已按 AI 建议更新画布（请检查后保存）');
    return false;
  }
}

/** 处理 `type === 'appendComponent'`：从组件库追加节点。 */
export class AppendComponentAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly editor: BpmEditorToken) {}

  supports(action: AiClientAction): boolean {
    const id = action.params?.['componentId'];
    return action.type === 'appendComponent' && typeof id === 'string' && !!id.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    const componentId = String(action.params?.['componentId'] ?? '').trim();
    const sourceRaw = action.params?.['sourceElementId'];
    const sourceElementId =
      typeof sourceRaw === 'string' && sourceRaw.trim() ? sourceRaw.trim() : null;
    try {
      this.editor.appendComponentForAi(componentId, sourceElementId);
      ctx.nzMessage.success('已追加组件到画布');
    } catch {
      ctx.nzMessage.error('追加组件失败');
    }
    return false;
  }
}

/** 处理 `type === 'toolbar'`：执行设计器工具栏白名单命令。 */
export class ToolbarAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly editor: BpmEditorToken) {}

  supports(action: AiClientAction): boolean {
    const cmd = action.params?.['toolbarCommand'];
    return action.type === 'toolbar' && typeof cmd === 'string' && !!cmd.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    const command = String(action.params?.['toolbarCommand'] ?? '').trim();
    const options = (action.params?.['toolbarOptions'] as Record<string, unknown> | undefined) ?? {};
    try {
      this.editor.runToolbarCommand(command, options);
      ctx.nzMessage.success(`已执行：${command}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '工具栏命令执行失败';
      ctx.nzMessage.error(msg);
    }
    return false;
  }
}

export function createBpmDesignerAssistantHandlers(
  editor: BpmEditorToken,
  getToolbar: () => BpmDesignerToolbarContext | undefined,
): AssistantActionHandler[] {
  return [
    new BpmnXmlAssistantActionHandler(getToolbar),
    new AppendComponentAssistantActionHandler(editor),
    new ToolbarAssistantActionHandler(editor),
  ];
}
