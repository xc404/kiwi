import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { SessionService } from '@app/core/services/common/session.service';
import { TokenKey } from '@config/constant';
import { environment } from '@env/environment';
import { BaseHttpService } from '@services/base-http.service';

export interface DesignerAgentChatMessage {
  role: 'user' | 'assistant' | string;
  text: string;
}

export interface DesignerAgentRunStatus {
  runId?: string;
  targetProcessId?: string;
  active: boolean;
  stage?: string;
  editPlanJson?: string;
  planDisplayJson?: string;
  candidateXml?: string;
  assistantReply?: string;
  askMessage?: string;
  pluginHintJson?: string;
  clarificationFormJson?: string;
  pendingHitlItemsJson?: string;
  issuesJson?: string;
  errorMessage?: string;
  planSkipped?: boolean;
  messages?: DesignerAgentChatMessage[];
  /** POST /actions 人机闸门：true=接受，false=拒绝 */
  gateAccepted?: boolean;
}

export interface DesignerAgentConfig {
  enabled: boolean;
}

export interface AgentStreamEvent {
  type?: string;
  runId?: string;
  stage?: string;
  label?: string;
  detail?: string;
  delta?: string;
  editPlanJson?: string;
  planDisplayJson?: string;
  summary?: string;
  planSkipped?: boolean;
  candidateXml?: string;
  askMessage?: string;
  pluginHintJson?: string;
  clarificationFormJson?: string;
  hitlItemJson?: string;
  issuesJson?: string;
  content?: string;
  errorMessage?: string;
  toolName?: string;
  argsPreview?: string;
}

export interface StartRunRequest {
  scenario: string;
  targetProcessId: string;
  selectedElementId?: string;
  baseBpmnXml?: string;
}

export interface FollowUpRequest {
  message: string;
  selectedElementId?: string;
  canvasBpmnXml?: string;
}

export type DesignerAgentActionType =
  | 'confirm_plan'
  | 'confirm_preview'
  | 'answer'
  | 'submit_clarification'
  | 'resume_install'
  | 'skip_install';

export interface DesignerAgentActionRequest {
  type: DesignerAgentActionType;
  confirmed?: boolean;
  editedPlanJson?: string;
  userAnswer?: string;
  feedbackText?: string;
  /** 输入框自然语言；由后端 LLM 判定意图（与 confirmed 互斥） */
  userMessage?: string;
  /** 当前画布 XML；用户可能在等待/预览期间手动改图 */
  canvasBpmnXml?: string;
  answers?: Record<string, string | string[]>;
  skippedQuestionIds?: string[];
  supplementalText?: string;
}

@Injectable({ providedIn: 'root' })
export class BpmDesignerAgentService {
  private readonly http = inject(BaseHttpService);
  private readonly session = inject(SessionService);

  fetchConfig(): Observable<DesignerAgentConfig> {
    return this.http.get<DesignerAgentConfig>('/bpm/designer-agent/config', { showLoading: false });
  }

  statusByTarget(targetProcessId: string): Observable<DesignerAgentRunStatus> {
    return this.http.get<DesignerAgentRunStatus>('/bpm/designer-agent/by-target', { targetProcessId }, {
      showLoading: false
    });
  }

  statusByRunId(runId: string): Observable<DesignerAgentRunStatus> {
    return this.http.get<DesignerAgentRunStatus>(`/bpm/designer-agent/runs/${runId}`, { showLoading: false });
  }

  /** 契约 ①：创建 run，JSON 返回状态 */
  createRun(body: StartRunRequest): Observable<DesignerAgentRunStatus> {
    return this.http.post<DesignerAgentRunStatus>('/bpm/designer-agent/runs', body, { showLoading: false });
  }

  followUp(runId: string, body: FollowUpRequest): Observable<DesignerAgentRunStatus> {
    return this.http.post<DesignerAgentRunStatus>(`/bpm/designer-agent/runs/${runId}/follow-up`, body, {
      showLoading: false
    });
  }

  clearSession(targetProcessId: string): Observable<DesignerAgentRunStatus> {
    const q = encodeURIComponent(targetProcessId);
    return this.http.post<DesignerAgentRunStatus>(`/bpm/designer-agent/sessions/clear?targetProcessId=${q}`, {}, {
      showLoading: false
    });
  }

  /** 契约 ③：统一人机操作 */
  submitAction(runId: string, action: DesignerAgentActionRequest): Observable<DesignerAgentRunStatus> {
    return this.http.post<DesignerAgentRunStatus>(`/bpm/designer-agent/runs/${runId}/actions`, action, {
      showLoading: false
    });
  }

  /** 契约 ④：仅订阅事件流（思考/工具/文本），状态请用 GET /runs/{id} */
  openEventStream(
    runId: string,
    onEvent: (event: AgentStreamEvent, eventName: string) => void,
    onError: (err: unknown) => void,
    onComplete: () => void
  ): AbortController {
    const controller = new AbortController();
    const token = this.session.getToken() ?? '';
    const url = `${environment.api.baseUrl}/bpm/designer-agent/runs/${runId}/events`;
    void fetch(url, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { [TokenKey]: token } : {})
      },
      signal: controller.signal
    })
      .then(res => this.consumeEventStream(res, onEvent, onComplete))
      .catch(err => {
        if ((err as Error).name !== 'AbortError') {
          onError(err);
        }
      });
    return controller;
  }

  private async consumeEventStream(
    res: Response,
    onEvent: (event: AgentStreamEvent, eventName: string) => void,
    onComplete: () => void
  ): Promise<void> {
    if (!res.ok || !res.body) {
      throw new Error(`事件流请求失败: ${res.status}`);
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split('\n\n');
      buffer = parts.pop() ?? '';
      for (const block of parts) {
        this.parseEventBlock(block, onEvent);
      }
    }
    onComplete();
  }

  private parseEventBlock(block: string, onEvent: (event: AgentStreamEvent, eventName: string) => void): void {
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim());
      }
    }
    if (dataLines.length === 0) {
      return;
    }
    try {
      const payload = JSON.parse(dataLines.join('\n')) as AgentStreamEvent;
      onEvent(payload, eventName);
    } catch {
      // ignore parse errors
    }
  }
}
