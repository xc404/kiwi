import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BaseHttpService } from '@services/base-http.service';
import type { AiChatMessage } from './ai-chat.service';

export type AiConversationScope = 'global' | 'bpm-designer';

export interface AiChatConversation {
  id?: string;
  ownerId?: string;
  scope?: AiConversationScope;
  scopeRef?: string;
  title?: string;
  searchText?: string;
  lastMessagePreview?: string;
  messageCount?: number;
  messages?: AiChatMessage[];
  createdTime?: string;
  updatedTime?: string;
}

export interface SpringPage<T> {
  content: T[];
  page?: {
    totalElements?: number;
    number?: number;
    size?: number;
  };
}

export interface CreateConversationRequest {
  scope?: AiConversationScope;
  scopeRef?: string;
  title?: string;
  messages?: AiChatMessage[];
}

export interface UpdateConversationRequest {
  mode?: 'append' | 'replace';
  title?: string;
  messages?: AiChatMessage[];
}

@Injectable({
  providedIn: 'root'
})
export class AiConversationService {
  private http = inject(BaseHttpService);

  list(params?: {
    scope?: AiConversationScope;
    scopeRef?: string;
    q?: string;
    page?: number;
    size?: number;
  }): Observable<SpringPage<AiChatConversation>> {
    return this.http.get<SpringPage<AiChatConversation>>('/ai/conversations', params ?? {}, {
      showLoading: false
    });
  }

  get(id: string): Observable<AiChatConversation> {
    return this.http.get<AiChatConversation>(`/ai/conversations/${id}`, undefined, { showLoading: false });
  }

  create(body: CreateConversationRequest): Observable<AiChatConversation> {
    return this.http.post<AiChatConversation>('/ai/conversations', body, { showLoading: false });
  }

  update(id: string, body: UpdateConversationRequest): Observable<AiChatConversation> {
    return this.http.put<AiChatConversation>(`/ai/conversations/${id}`, body, { showLoading: false });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`/ai/conversations/${id}`, undefined, { showLoading: false });
  }
}
