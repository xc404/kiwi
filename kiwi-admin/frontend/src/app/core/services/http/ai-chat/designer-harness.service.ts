import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BaseHttpService } from '@services/base-http.service';

export interface DesignerHarnessChatMessage {
  id?: string;
  role: string;
  text: string;
  createdAt?: string;
}

export interface DesignerHarnessStatus {
  targetProcessId: string;
  running: boolean;
  inputEnabled: boolean;
  errorMessage?: string | null;
  bpmnXml?: string | null;
  messages: DesignerHarnessChatMessage[];
}

export interface DesignerHarnessTurnBody {
  targetProcessId: string;
  message: string;
  canvasBpmnXml?: string;
  selectedElementId?: string | null;
}

export interface DesignerHarnessConfig {
  enabled: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class DesignerHarnessService {
  private readonly http = inject(BaseHttpService);
  private readonly base = '/bpm/designer-harness';

  fetchConfig(): Observable<DesignerHarnessConfig> {
    return this.http.get<DesignerHarnessConfig>(`${this.base}/config`, {}, { showLoading: false });
  }

  getSession(targetProcessId: string): Observable<DesignerHarnessStatus> {
    return this.http.get<DesignerHarnessStatus>(`${this.base}/sessions`, { targetProcessId }, { showLoading: false });
  }

  clear(targetProcessId: string): Observable<DesignerHarnessStatus> {
    return this.http.post<DesignerHarnessStatus>(`${this.base}/sessions/clear?targetProcessId=${encodeURIComponent(targetProcessId)}`, {}, { showLoading: false });
  }

  postTurn(body: DesignerHarnessTurnBody): Observable<DesignerHarnessStatus> {
    return this.http.post<DesignerHarnessStatus>(`${this.base}/sessions/turns`, body, { showLoading: false });
  }
}
