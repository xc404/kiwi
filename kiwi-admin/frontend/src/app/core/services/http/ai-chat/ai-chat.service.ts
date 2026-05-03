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

export interface BpmDesignerActionDto {
  type: string;
  toolbarCommand?: string;
  toolbarOptions?: Record<string, unknown>;
  xml?: string;
  componentId?: string;
  sourceElementId?: string;
  path?: string;
  queryParams?: Record<string, string>;
}

export interface BpmDesignerClientCapabilities {
  toolbarCommands?: string[];
  allowBpmnXml?: boolean;
  allowAppendComponent?: boolean;
  allowNavigate?: boolean;
  /** 可选：前端当前可见组件 id -> 名称。 */
  availableComponents?: Record<string, string>;
}

export interface BpmDesignerAssistantRequest {
  messages: AiChatMessage[];
  processId: string;
  /** 画布当前 BPMN（含未保存修改）；不传则后端用库中版本拼上下文 */
  bpmnXml?: string;
  /** 由前端声明动作能力，避免命令白名单写死在后端 */
  clientCapabilities?: BpmDesignerClientCapabilities;
}

export interface BpmDesignerAssistantResponse {
  content: string;
  actions?: BpmDesignerActionDto[];
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

  /** BPM 设计器专用：工具栏 / 导入 XML / 追加组件 / 跳转 */
  bpmDesigner(body: BpmDesignerAssistantRequest) {
    return this.http.post<BpmDesignerAssistantResponse>('/ai/bpm-designer', body, { showLoading: false });
  }
}
