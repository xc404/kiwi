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

/** 与后端 AiAssistantResponse.actions 项对齐；含 BPM 设计器工具产生的扩展字段。 */
export interface AiClientAction {
  type: string;
  path?: string;
  queryParams?: Record<string, string>;
  toolbarCommand?: string;
  toolbarOptions?: Record<string, unknown>;
  xml?: string;
  componentId?: string;
  sourceElementId?: string;
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
