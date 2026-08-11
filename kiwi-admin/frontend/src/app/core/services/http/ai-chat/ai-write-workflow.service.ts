import { inject, Injectable } from '@angular/core';

import { BaseHttpService } from '@services/base-http.service';

export interface WriteWorkflowStatus {
  sessionId?: string;
  targetProcessId?: string;
  active: boolean;
  stage?: string;
  dispatchCode?: string;
  candidateXml?: string;
  assistantReply?: string;
  askMessage?: string;
  pluginHintJson?: string;
  issuesJson?: string;
  catalogJson?: string;
  errorMessage?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AiWriteWorkflowService {
  private readonly http = inject(BaseHttpService);

  statusByTarget(targetProcessId: string) {
    return this.http.get<WriteWorkflowStatus>('/ai/write-workflow/by-target', { targetProcessId }, {
      showLoading: false
    });
  }

  confirmPreview(sessionId: string, confirmed: boolean) {
    return this.http.post<WriteWorkflowStatus>(
      `/ai/write-workflow/sessions/${sessionId}/confirm-preview`,
      { confirmed },
      { showLoading: true }
    );
  }

  confirmInstall(sessionId: string, accepted: boolean) {
    return this.http.post<WriteWorkflowStatus>(
      `/ai/write-workflow/sessions/${sessionId}/confirm-install`,
      { accepted },
      { showLoading: true }
    );
  }

  answer(sessionId: string, userAnswer: string) {
    return this.http.post<WriteWorkflowStatus>(
      `/ai/write-workflow/sessions/${sessionId}/answer`,
      { userAnswer },
      { showLoading: true }
    );
  }
}
