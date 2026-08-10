import { inject, Injectable } from '@angular/core';

import { BaseHttpService } from '@services/base-http.service';

export interface AiAuthoringTaskInfo {
  id: string;
  name: string;
  taskDefinitionKey: string;
  assignee?: string;
}

export interface AiAuthoringStatus {
  processInstanceId?: string;
  businessKey?: string;
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
  tasks?: AiAuthoringTaskInfo[];
}

@Injectable({
  providedIn: 'root'
})
export class AiWorkflowAuthoringService {
  private readonly http = inject(BaseHttpService);

  start(body: {
    scenario: string;
    targetProcessId: string;
    selectedElementId?: string | null;
    baseBpmnXml?: string | null;
  }) {
    return this.http.post<AiAuthoringStatus>('/ai/workflow-authoring/start', body, { showLoading: true });
  }

  status(processInstanceId: string) {
    return this.http.get<AiAuthoringStatus>(`/ai/workflow-authoring/${processInstanceId}`, { showLoading: false });
  }

  statusByTarget(targetProcessId: string) {
    return this.http.get<AiAuthoringStatus>('/ai/workflow-authoring/by-target', { targetProcessId }, {
      showLoading: false
    });
  }

  completeTask(taskId: string, variables: Record<string, unknown>) {
    return this.http.post<AiAuthoringStatus>(`/ai/workflow-authoring/tasks/${taskId}/complete`, { variables }, {
      showLoading: true
    });
  }
}
