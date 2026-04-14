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

export interface AiAssistantClientAction {
  type: string;
  path?: string;
  queryParams?: Record<string, string>;
}

export interface AiAssistantResponse {
  content: string;
  actions?: AiAssistantClientAction[];
}

@Injectable({
  providedIn: 'root'
})
export class AiChatService {
  private http = inject(BaseHttpService);

  chat(body: AiChatRequest) {
    return this.http.post<AiChatResponse>('/ai/chat', body, { showLoading: false });
  }

  /** 可返回 navigate 等动作（path 与菜单路由一致） */
  assistant(body: AiChatRequest) {
    return this.http.post<AiAssistantResponse>('/ai/assistant', body, { showLoading: false });
  }
}
