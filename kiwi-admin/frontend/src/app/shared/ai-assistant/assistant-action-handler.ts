import { Router } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd/message';

import type { AiClientAction } from '@services/ai-chat/ai-chat.service';

/** 供各 handler 使用的 Angular 服务入口（可随需求扩展字段）。 */
export interface AssistantActionContext {
  readonly router: Router;
  readonly nzMessage: NzMessageService;
}

/**
 * 助手客户端动作（AiClientAction）的策略处理器。
 * 编排顺序见 {@link AssistantActionOrchestratorService}。
 */
export interface AssistantActionHandler {
  supports(action: AiClientAction): boolean;

  /**
   * @returns `true` 时编排器将停止处理同一次响应中剩余的 action（与历史「首个 navigate 后即 break」一致）。
   */
  handle(action: AiClientAction, ctx: AssistantActionContext): boolean;
}
