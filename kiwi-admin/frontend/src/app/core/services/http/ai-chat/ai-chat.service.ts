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

@Injectable({
  providedIn: 'root'
})
export class AiChatService {
  private http = inject(BaseHttpService);

  chat(body: AiChatRequest) {
    return this.http.post<AiChatResponse>('/ai/chat', body, { showLoading: false });
  }
}
