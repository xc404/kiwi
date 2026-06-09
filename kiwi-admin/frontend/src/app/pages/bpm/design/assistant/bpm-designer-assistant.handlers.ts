import type { AiClientAction } from '@services/ai-chat/ai-chat.service';
import type { AssistantActionContext, AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';

import { BpmAppendAnchorRequiredError } from '../service/bpm-append-anchor-required.error';

export interface BpmDesignerAssistantDeps {
  /** 导入 BPMN 并保存到当前流程（设计器 AI 改图主路径） */
  importBpmnXmlAndSave: (xml: string) => Promise<void>;
  /** 服务端 matchComponent / 兼容 appendComponent：由前端解析锚点并追加 */
  applyMatchedComponent: (componentId: string, sourceElementId?: string | null) => void;
  runToolbarCommand: (command: string, options?: Record<string, unknown>) => void;
}

/** 处理 `type === 'bpmnXml'`：替换当前画布 BPMN。 */
export class BpmnXmlAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly deps: Pick<BpmDesignerAssistantDeps, 'importBpmnXmlAndSave'>) {}

  supports(action: AiClientAction): boolean {
    const xml = action.params?.['xml'];
    return action.type === 'bpmnXml' && typeof xml === 'string' && !!xml.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    const xml = String(action.params?.['xml'] ?? '');
    void this.deps
      .importBpmnXmlAndSave(xml)
      .then(() => {
        ctx.nzMessage.success('已按 AI 更新流程并已保存');
      })
      .catch(e => {
        const msg = e instanceof Error ? e.message : '导入或保存失败';
        if (msg && !msg.includes('导入失败')) {
          ctx.nzMessage.error(msg);
        }
      });
    return false;
  }
}

function applyMatchedComponentAction(deps: Pick<BpmDesignerAssistantDeps, 'applyMatchedComponent'>, action: AiClientAction, ctx: AssistantActionContext, sourceElementId: string | null): boolean {
  const componentId = String(action.params?.['componentId'] ?? '').trim();
  const componentName = String(action.params?.['componentName'] ?? componentId);
  try {
    deps.applyMatchedComponent(componentId, sourceElementId);
    ctx.nzMessage.success(`已将「${componentName}」追加到画布`);
  } catch (e) {
    if (e instanceof BpmAppendAnchorRequiredError) {
      ctx.nzMessage.warning(e.message);
    } else {
      const msg = e instanceof Error ? e.message : '追加组件失败';
      ctx.nzMessage.error(msg);
    }
  }
  return false;
}

/** 处理 `type === 'matchComponent'`：服务端仅返回匹配组件，前端追加到画布。 */
export class MatchComponentAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly deps: Pick<BpmDesignerAssistantDeps, 'applyMatchedComponent'>) {}

  supports(action: AiClientAction): boolean {
    const id = action.params?.['componentId'];
    return action.type === 'matchComponent' && typeof id === 'string' && !!id.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    return applyMatchedComponentAction(this.deps, action, ctx, null);
  }
}

/** 兼容旧 `appendComponent`：仍走前端追加，sourceElementId 仅当用户/模型明确给出 */
export class AppendComponentAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly deps: Pick<BpmDesignerAssistantDeps, 'applyMatchedComponent'>) {}

  supports(action: AiClientAction): boolean {
    const id = action.params?.['componentId'];
    return action.type === 'appendComponent' && typeof id === 'string' && !!id.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    const sourceRaw = action.params?.['sourceElementId'];
    const sourceElementId = typeof sourceRaw === 'string' && sourceRaw.trim() ? sourceRaw.trim() : null;
    return applyMatchedComponentAction(this.deps, action, ctx, sourceElementId);
  }
}

/** 处理 `type === 'toolbar'`：执行设计器工具栏白名单命令。 */
export class ToolbarAssistantActionHandler implements AssistantActionHandler {
  constructor(private readonly deps: Pick<BpmDesignerAssistantDeps, 'runToolbarCommand'>) {}

  supports(action: AiClientAction): boolean {
    const cmd = action.params?.['toolbarCommand'];
    return action.type === 'toolbar' && typeof cmd === 'string' && !!cmd.trim();
  }

  handle(action: AiClientAction, ctx: AssistantActionContext): boolean {
    const command = String(action.params?.['toolbarCommand'] ?? '').trim();
    const options = (action.params?.['toolbarOptions'] as Record<string, unknown> | undefined) ?? {};
    try {
      this.deps.runToolbarCommand(command, options);
      ctx.nzMessage.success(`已执行：${command}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '工具栏命令执行失败';
      ctx.nzMessage.error(msg);
    }
    return false;
  }
}

export function createBpmDesignerAssistantHandlers(deps: BpmDesignerAssistantDeps): AssistantActionHandler[] {
  return [new BpmnXmlAssistantActionHandler(deps), new MatchComponentAssistantActionHandler(deps), new AppendComponentAssistantActionHandler(deps), new ToolbarAssistantActionHandler(deps)];
}
