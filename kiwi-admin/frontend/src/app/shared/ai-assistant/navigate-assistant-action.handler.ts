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
    const path = action.params?.['path'];
    return action.type === 'navigate' && typeof path === 'string' && !!path.trim();
  }

  handle(action: AiClientAction, _ctx: AssistantActionContext): boolean {
    const path = String(action.params?.['path'] ?? '');
    const queryParams = (action.params?.['queryParams'] as Record<string, string> | undefined) ?? {};
    const raw = path.replace(/^\/+/, '');
    const segments = raw.split('/').filter(Boolean);
    void this.router.navigate(segments, { queryParams }).catch(() => {
      this.nzMessage.warning('无法打开目标页面');
    });
    return true;
  }
}
