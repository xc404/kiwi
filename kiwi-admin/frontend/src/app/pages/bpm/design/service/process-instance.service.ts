import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { environment } from '@env/environment';
import { Observable, map } from 'rxjs';

/**
 * 与后端 {@code BpmProcessInstanceDto} 一致（单实例查询 {@code GET /bpm/process-instance/{id}}）。
 */
/** 未关闭的引擎 Incident（与后端 BpmOpenIncidentDto 一致） */
export interface BpmOpenIncidentDto {
  incidentId?: string;
  incidentType?: string | null;
  message?: string | null;
  activityId?: string | null;
  activityName?: string | null;
}

/** 与后端 ProcessInstanceState 枚举序列化一致 */
export type BpmProcessInstanceState =
  | 'RUNNING'
  | 'SUSPENDED'
  | 'COMPLETED'
  | 'CANCELED'
  | 'ACTIVE'
  | 'ERROR';

export interface BpmProcessInstanceDto {
  id?: string;
  businessKey?: string;
  processDefinitionId?: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  startTime?: string;
  tenantId?: string | null;
  state?: BpmProcessInstanceState | string;
  ended?: boolean;
  suspended?: boolean;
  endTime?: string | null;
  deleteReason?: string | null;
  currentActivities?: unknown[];
  openIncidents?: BpmOpenIncidentDto[];
  [key: string]: unknown;
}

/** Camunda GET /process-definition/{id} 常用字段 */
export interface CamundaProcessDefinition {
  id?: string;
  key?: string;
  name?: string | null;
  version?: number;
  [key: string]: unknown;
}

/** Camunda GET /history/variable-instance 列表项 */
export interface CamundaHistoricVariableInstance {
  name: string | null;
  type?: string | null;
  value?: unknown;
  /** 形如 `Activity_xxx:uuid`，前缀为画布节点 id */
  activityInstanceId?: string | null;
  /** Camunda 历史变量创建时间（ISO 8601） */
  createTime?: string | null;
  [key: string]: unknown;
}

/**
 * 画布/属性区语义：已结束 | 异常 | 运行中 | 未运行（无历史活动记录）。
 */
export type BpmActivityVisualState = 'completed' | 'error' | 'running' | 'notStarted';

/** Camunda History Activity Instance（GET /history/activity-instance） */
export interface CamundaHistoricActivityInstance {
  id?: string;
  activityId: string | null;
  activityType: string | null;
  startTime?: string | null;
  endTime: string | null;
  /** Camunda 原生字段：已取消的活动实例（可与 endTime 同时存在） */
  canceled?: boolean;
  /** 关联的未关闭 incident id 列表（存在时表示该活动处于异常态） */
  incidentIds?: string[] | null;
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
  private readonly baseHttp = inject(BaseHttpService);

  private engineRestRoot(): string {
    return `${environment.api.baseUrl}${environment.api.camundaEngineRestPath}`;
  }

  /**
   * 单实例详情：{@code GET /bpm/process-instance/{instanceId}}（运行中与已结束均由后端统一解析）。
   */
  getProcessInstance(processInstanceId: string): Observable<BpmProcessInstanceDto> {
    return this.baseHttp.get<BpmProcessInstanceDto>(
      `/bpm/process-instance/${encodeURIComponent(processInstanceId)}`,
    );
  }

  /** GET /process-definition/{id}/xml */
  getProcessDefinitionXml(processDefinitionId: string): Observable<CamundaProcessDefinitionXml> {
    return this.http.get<CamundaProcessDefinitionXml>(
      `${this.engineRestRoot()}/process-definition/${encodeURIComponent(processDefinitionId)}/xml`,
    );
  }

  /** GET /process-definition/{id}（Camunda 含 name、key、version 等） */
  getProcessDefinition(processDefinitionId: string): Observable<CamundaProcessDefinition> {
    return this.http.get<CamundaProcessDefinition>(
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
        const completed = item.endTime != null && item.endTime !== '';
        return {
          ...item,
          completed,
          active: !completed,
        };
      });
    }));
  }


  // /** GET /process-instance/{id}/variables */
  // getRuntimeProcessInstanceVariables(processInstanceId: string): Observable<Record<string, any>> {
  //   return this.http.get<Record<string, any>>(
  //     `${this.engineRestRoot()}/process-instance/${encodeURIComponent(processInstanceId)}/variables`
  //   );
  // }

  /**
   * GET /history/variable-instance?processInstanceId={id}
   * Camunda 返回变量实例数组；映射为与 GET /process-instance/{id}/variables 相近的 Record（name -> { type, value }）。
   * 同名变量多次出现时后者覆盖前者（与历史 API 列表顺序一致）。
   */
  getProcessInstanceVariables(processInstanceId: string): Observable<CamundaHistoricVariableInstance[]> {
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
