import { isDevMode, Injectable, Inject, Optional } from '@angular/core';
import { Router } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd/message';

import type { AiClientAction } from '@services/ai-chat/ai-chat.service';

import { ASSISTANT_ACTION_HANDLERS } from './assistant-action-handlers.token';
import type { AssistantActionContext, AssistantActionHandler } from './assistant-action-handler';
import { NavigateAssistantActionHandler } from './navigate-assistant-action.handler';

@Injectable({ providedIn: 'root' })
export class AssistantActionOrchestratorService {
  private readonly navigateHandler: NavigateAssistantActionHandler;
  private readonly ctx: AssistantActionContext;

  constructor(
    router: Router,
    nzMessage: NzMessageService,
    @Optional()
    @Inject(ASSISTANT_ACTION_HANDLERS)
    private readonly fromToken: AssistantActionHandler[] | undefined
  ) {
    this.navigateHandler = new NavigateAssistantActionHandler(router, nzMessage);
    this.ctx = { router, nzMessage };
  }

  /**
   * 按 design：input → multi token → 内置 navigate；每个 action 仅首个匹配的 handler 执行。
   * 若 navigate 成功认领则不再处理后续 action（与旧 `ChatComponent` 行为一致）。
   */
  dispatch(
    actions: AiClientAction[] | undefined,
    inputHandlers: readonly AssistantActionHandler[]
  ): void {
    if (!actions?.length) {
      return;
    }
    const tokenHandlers = this.fromToken ?? ([] as AssistantActionHandler[]);
    const chain: AssistantActionHandler[] = [...inputHandlers, ...tokenHandlers, this.navigateHandler];

    for (const action of actions) {
      let matched = false;
      for (const h of chain) {
        if (h.supports(action)) {
          matched = true;
          const stop = h.handle(action, this.ctx);
          if (stop) {
            return;
          }
          break;
        }
      }
      if (!matched && isDevMode()) {
        // eslint-disable-next-line no-console -- 开发期诊断未知 action，符合 spec
        console.warn('[AssistantActionOrchestrator] 未识别的 assistant action:', action);
      }
    }
  }
}
