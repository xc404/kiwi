import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { SessionService } from '@app/core/services/common/session.service';
import { TokenKey } from '@config/constant';
import { environment } from '@env/environment';
import { BaseHttpService } from '@services/base-http.service';

export interface DesignerAgentRunStatus {
  runId?: string;
  targetProcessId?: string;
  active: boolean;
  stage?: string;
  editPlanJson?: string;
  candidateXml?: string;
  assistantReply?: string;
  askMessage?: string;
  pluginHintJson?: string;
  issuesJson?: string;
  errorMessage?: string;
  planSkipped?: boolean;
}

export interface AgentStreamEvent {
  type?: string;
  runId?: string;
  stage?: string;
  label?: string;
  detail?: string;
  delta?: string;
  editPlanJson?: string;
  summary?: string;
  planSkipped?: boolean;
  candidateXml?: string;
  askMessage?: string;
  pluginHintJson?: string;
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

@Injectable({ providedIn: 'root' })
export class BpmDesignerAgentService {
  private readonly http = inject(BaseHttpService);
  private readonly session = inject(SessionService);

  statusByTarget(targetProcessId: string): Observable<DesignerAgentRunStatus> {
    return this.http.get<DesignerAgentRunStatus>('/bpm/designer-agent/by-target', { targetProcessId }, {
      showLoading: false
    });
  }

  confirmPlan(runId: string, confirmed: boolean, editedPlanJson?: string): Observable<DesignerAgentRunStatus> {
    return this.http.post<DesignerAgentRunStatus>(`/bpm/designer-agent/runs/${runId}/confirm-plan`, {
      confirmed,
      editedPlanJson
    });
  }

  confirmPreview(runId: string, confirmed: boolean): Observable<DesignerAgentRunStatus> {
    return this.http.post<DesignerAgentRunStatus>(`/bpm/designer-agent/runs/${runId}/confirm-preview`, { confirmed });
  }

  answer(runId: string, userAnswer: string): Observable<DesignerAgentRunStatus> {
    return this.http.post<DesignerAgentRunStatus>(`/bpm/designer-agent/runs/${runId}/answer`, { userAnswer });
  }

  startRunStream(
    body: StartRunRequest,
    onEvent: (event: AgentStreamEvent, eventName: string) => void,
    onError: (err: unknown) => void,
    onComplete: () => void
  ): AbortController {
    const controller = new AbortController();
    const token = this.session.getToken() ?? '';
    const url = `${environment.api.baseUrl}/bpm/designer-agent/runs/stream`;
    void fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { [TokenKey]: token } : {})
      },
      body: JSON.stringify(body),
      signal: controller.signal
    })
      .then(res => this.consumeSseResponse(res, onEvent, onComplete))
      .catch(err => {
        if ((err as Error).name !== 'AbortError') {
          onError(err);
        }
      });
    return controller;
  }

  resumeRunStream(
    runId: string,
    onEvent: (event: AgentStreamEvent, eventName: string) => void,
    onError: (err: unknown) => void,
    onComplete: () => void
  ): AbortController {
    const controller = new AbortController();
    const token = this.session.getToken() ?? '';
    const url = `${environment.api.baseUrl}/bpm/designer-agent/runs/${runId}/stream/resume`;
    void fetch(url, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { [TokenKey]: token } : {})
      },
      signal: controller.signal
    })
      .then(res => this.consumeSseResponse(res, onEvent, onComplete))
      .catch(err => {
        if ((err as Error).name !== 'AbortError') {
          onError(err);
        }
      });
    return controller;
  }

  private async consumeSseResponse(
    res: Response,
    onEvent: (event: AgentStreamEvent, eventName: string) => void,
    onComplete: () => void
  ): Promise<void> {
    if (!res.ok || !res.body) {
      throw new Error(`SSE 请求失败: ${res.status}`);
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
        this.parseSseBlock(block, onEvent);
      }
    }
    onComplete();
  }

  private parseSseBlock(block: string, onEvent: (event: AgentStreamEvent, eventName: string) => void): void {
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
      const payload = JSON.parse(dataLines.join('\n')) as AgentStreamEvent | DesignerAgentRunStatus;
      onEvent(payload as AgentStreamEvent, eventName);
    } catch {
      // ignore parse errors
    }
  }
}
