import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, map, pipe } from 'rxjs';

/**
 * 统一后的流程实例视图（由 {@link ProcessInstanceService} 从运行时 / 历史 API 归一化）。
 * Camunda 历史接口使用 processDefinitionId、endTime、state；运行时使用 definitionId、ended、suspended。
 */
export interface ProcessInstance {
  id: string;
  /** 流程定义 ID（已从历史字段 processDefinitionId 对齐） */
  definitionId: string;
  processDefinitionKey?: string;
  businessKey?: string;
  caseInstanceId?: string;
  ended?: boolean;
  suspended?: boolean;
  tenantId?: string | null;
  [key: string]: any; // 可扩展字段（含 API 原始字段）
}

/** Camunda GET /process-instance/{id} 与 GET /history/process-instance/{id} 原始响应（字段因端点而异） */
type CamundaProcessInstanceResponse = Record<string, any>;

/** Camunda GET /history/variable-instance 列表项 */
export interface CamundaHistoricVariableInstance {
  name: string | null;
  type?: string | null;
  value?: unknown;
  [key: string]: unknown;
}

/** Camunda History Activity Instance（GET /history/activity-instance） */
export interface CamundaHistoricActivityInstance {
  id?: string;
  activityId: string | null;
  activityType: string | null;
  endTime: string | null;
  completed: boolean;
  active: boolean;
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

  /**
   * 将运行时或历史 API 返回的流程实例转为统一的 {@link ProcessInstance}。
   */
  private normalizeProcessInstance(raw: CamundaProcessInstanceResponse): ProcessInstance {
    const definitionId = String(raw['definitionId'] ?? raw['processDefinitionId'] ?? '').trim();
    const historicEndedByTime = raw['endTime'] != null && raw['endTime'] !== '';
    const state = raw['state'];
    const historicEndedByState =
      typeof state === 'string' &&
      ['COMPLETED', 'INTERNALLY_TERMINATED', 'EXTERNALLY_TERMINATED'].includes(state);
    const ended = raw['ended'] === true || historicEndedByTime || historicEndedByState;
    const suspended = raw['suspended'] === true || state === 'SUSPENDED';
    return {
      ...raw,
      id: String(raw['id'] ?? ''),
      definitionId,
      processDefinitionKey: raw['processDefinitionKey'] || raw['definitionKey'],
      businessKey: raw['businessKey'],
      caseInstanceId: raw['caseInstanceId'],
      ended,
      suspended,
      tenantId: raw['tenantId'],
    };
  }

  /** GET /process-instance/{id} */
  getRuntimeProcessInstance(processInstanceId: string): Observable<ProcessInstance> {
    return this.http
      .get<CamundaProcessInstanceResponse>(
        `${this.engineRestRoot()}/process-instance/${encodeURIComponent(processInstanceId)}`,
      )
      .pipe(map((raw) => this.normalizeProcessInstance(raw)));
  }

  /** GET /history/process-instance/{id} */
  getHistoricProcessInstance(processInstanceId: string): Observable<ProcessInstance> {
    return this.http
      .get<CamundaProcessInstanceResponse>(
        `${this.engineRestRoot()}/history/process-instance/${encodeURIComponent(processInstanceId)}`,
      )
      .pipe(map((raw) => this.normalizeProcessInstance(raw)));
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
    ) 
    .pipe(map((items) => {
      return items.map((item) => {
        return {
          ...item,
          completed: item.endTime != null && item.endTime !== '',
          active: item.endTime == null || item.endTime === '',
        };
      });
    }));
  }

  // getProcessInstanceVariables(processInstanceId: string, runtime = true): Observable<Record<string, any>> {
  //   if (runtime) {
  //     return this.getRuntimeProcessInstanceVariables(processInstanceId);
  //   }
  //   return this.getHistoricProcessInstanceVariables(processInstanceId);
  // }


  /** GET /process-instance/{id}/variables */
  getRuntimeProcessInstanceVariables(processInstanceId: string): Observable<Record<string, any>> {
    return this.http.get<Record<string, any>>(
      `${this.engineRestRoot()}/process-instance/${encodeURIComponent(processInstanceId)}/variables`
    );
  }

  /**
   * GET /history/variable-instance?processInstanceId={id}
   * Camunda 返回变量实例数组；映射为与 GET /process-instance/{id}/variables 相近的 Record（name -> { type, value }）。
   * 同名变量多次出现时后者覆盖前者（与历史 API 列表顺序一致）。
   */
  getHistoricProcessInstanceVariables(processInstanceId: string): Observable<CamundaHistoricVariableInstance[]> {
    return this.http
      .get<CamundaHistoricVariableInstance[]>(`${this.engineRestRoot()}/history/variable-instance`, {
        params: { processInstanceId },
      });
      // .pipe(
      //   map((items) => {
      //     const out: Record<string, any> = {};
      //     for (const item of items) {
      //       const name = item.name;
      //       if (name == null || name === '') {
      //         continue;
      //       }
      //       out[name] = {
      //         type: item.type ?? null,
      //         value: item.value,
      //         valueInfo: {},
      //       };
      //     }
      //     return out;
      //   }),
      // );
  }
}
