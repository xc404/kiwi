import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable } from 'rxjs';


/** Camunda History Activity Instance（GET /history/activity-instance） */
export interface CamundaHistoricActivityInstance {
  id?: string;
  activityId: string | null;
  activityType: string | null;
  endTime: string | null;
}

/** 流程定义 XML（GET /process-definition/{id}/xml） */
export interface CamundaProcessDefinitionXml {
  bpmn20Xml: string;
}

@Injectable({
  providedIn: 'root',
})
export class ProcessInstanceService {
  private readonly http = inject(HttpClient);

  private engineRestRoot(): string {
    return `${environment.api.baseUrl}${environment.api.camundaEngineRestPath}`;
  }

  /** GET /process-instance/{id} */
  getRuntimeProcessInstance(processInstanceId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(
      `${this.engineRestRoot()}/process-instance/${encodeURIComponent(processInstanceId)}`,
    );
  }

  /** GET /history/process-instance/{id} */
  getHistoricProcessInstance(processInstanceId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(
      `${this.engineRestRoot()}/history/process-instance/${encodeURIComponent(processInstanceId)}`,
    );
  }

  /** GET /process-definition/{id}/xml */
  getProcessDefinitionXml(processDefinitionId: string): Observable<CamundaProcessDefinitionXml> {
    return this.http.get<CamundaProcessDefinitionXml>(
      `${this.engineRestRoot()}/process-definition/${encodeURIComponent(processDefinitionId)}/xml`,
    );
  }

  /** GET /process-definition/{id} */
  getProcessDefinition(processDefinitionId: string): Observable<{ key?: string }> {
    return this.http.get<{ key?: string }>(
      `${this.engineRestRoot()}/process-definition/${encodeURIComponent(processDefinitionId)}`,
    );
  }

  /** GET /history/activity-instance */
  getHistoryActivityInstances(processInstanceId: string): Observable<CamundaHistoricActivityInstance[]> {
    return this.http.get<CamundaHistoricActivityInstance[]>(
      `${this.engineRestRoot()}/history/activity-instance`,
      {
        params: {
          processInstanceId,
          sortBy: 'startTime',
          sortOrder: 'asc',
        },
      },
    );
  }
}
