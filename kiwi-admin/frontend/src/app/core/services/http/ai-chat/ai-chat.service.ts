import { inject, Injectable } from '@angular/core';

import { BaseHttpService } from '@services/base-http.service';

export interface AiChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

export interface AiChatRequest {
  messages: AiChatMessage[];
}

export interface AiChatResponse {
  content: string;
}

/**
 * 与后端 ClientAction 对齐：type 为动作语义，params 键名由前后端约定。
 * 例如 navigate → { path, queryParams? }；toolbar → { toolbarCommand, toolbarOptions? }。
 */
export interface AiClientAction {
  type: string;
  params?: Record<string, unknown>;
}

export interface AiAssistantResponse {
  content: string;
  actions?: AiClientAction[];
}

@Injectable({
  providedIn: 'root'
})
export class AiChatService {
  private http = inject(BaseHttpService);

  chat(body: AiChatRequest) {
    return this.http.post<AiChatResponse>('/ai/chat', body, { showLoading: false });
  }

  /**
   * 统一助手：模型通过 MCP 自选工具；响应含 content 与可编排的 actions。
   * BPM 等场景由前端在 messages 中附带 BPMN、流程 id、能力说明等上下文，无需单独接口。
   */
  assistant(body: AiChatRequest) {
    return this.http.post<AiAssistantResponse>('/ai/assistant', body, { showLoading: false });
  }
}
