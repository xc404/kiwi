import { InjectionToken } from '@angular/core';

import type { AssistantActionHandler } from './assistant-action-handler';

/**
 * 全局/模块级额外客户端动作处理器（multi provider）。
 * 编排顺序：`ChatComponent.actionHandlers` → 本 token → 内置 navigate。
 */
export const ASSISTANT_ACTION_HANDLERS = new InjectionToken<AssistantActionHandler>(
  'ASSISTANT_ACTION_HANDLERS'
);
