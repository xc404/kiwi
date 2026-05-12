import { Router } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd/message';

import type { AiClientAction } from '@services/ai-chat/ai-chat.service';

import type { AssistantActionContext, AssistantActionHandler } from './assistant-action-handler';

/** 内置：处理 `type === 'navigate'` 的菜单路由跳转。 */
export class NavigateAssistantActionHandler implements AssistantActionHandler {
  constructor(
    private readonly router: Router,
    private readonly nzMessage: NzMessageService
  ) {}

  supports(action: AiClientAction): boolean {
    return action.type === 'navigate' && !!action.path?.trim();
  }

  handle(action: AiClientAction, _ctx: AssistantActionContext): boolean {
    const raw = action.path!.replace(/^\/+/, '');
    const segments = raw.split('/').filter(Boolean);
    void this.router.navigate(segments, { queryParams: action.queryParams ?? {} }).catch(() => {
      this.nzMessage.warning('无法打开目标页面');
    });
    return true;
  }
}
