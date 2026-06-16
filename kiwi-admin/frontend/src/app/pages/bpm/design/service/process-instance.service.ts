import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { ArrayResult } from '@app/core/services/types';

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
export type BpmProcessInstanceState = 'RUNNING' | 'SUSPENDED' | 'COMPLETED' | 'CANCELED' | 'ACTIVE' | 'ERROR';

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

/** 历史变量（GET /bpm/process-instance/{id}/variables） */
export interface CamundaHistoricVariableInstance {
  name: string | null;
  type?: string | null;
  value?: unknown;
  /** 形如 `Activity_xxx:uuid`，前缀为画布节点 id */
  activityInstanceId?: string | null;
  /** 历史变量创建时间（ISO 8601） */
  createTime?: string | null;
  [key: string]: unknown;
}

/**
 * 画布/属性区语义：已结束 | 异常 | 运行中 | 未运行（无历史活动记录）。
 */
export type BpmActivityVisualState = 'completed' | 'error' | 'running' | 'notStarted';

/** 历史活动（GET /bpm/process-instance/{id}/history-activities） */
export interface CamundaHistoricActivityInstance {
  id?: string;
  activityId: string | null;
  activityType: string | null;
  startTime?: string | null;
  endTime: string | null;
  /** 已取消的活动实例（可与 endTime 同时存在） */
  canceled?: boolean;
  /** 关联的未关闭 incident id 列表（存在时表示该活动处于异常态） */
  incidentIds?: string[] | null;
  /** CallActivity 被调用的子流程实例 ID */
  calledProcessInstanceId?: string | null;
  completed: boolean;
  active: boolean;
}

/** 流程定义 XML（GET /bpm/process-instance/{id}/definition-xml） */
export interface CamundaProcessDefinitionXml {
  bpmn20Xml: string;
}

/** 与后端 BpmInstanceRecoverResultDto 一致：一键恢复 OPEN incident 的结果摘要 */
export interface BpmInstanceRecoverResultDto {
  openIncidentCount: number;
  jobsRetried: number;
  externalTasksRetried: number;
  incidentsSkipped: number;
  retriesApplied: number;
}

@Injectable({
  providedIn: 'root'
})
export class ProcessInstanceService {
  private readonly baseHttp = inject(BaseHttpService);

  /**
   * 单实例详情：{@code GET /bpm/process-instance/{instanceId}}（运行中与已结束均由后端统一解析）。
   */
  getProcessInstance(processInstanceId: string): Observable<BpmProcessInstanceDto> {
    return this.baseHttp.get<BpmProcessInstanceDto>(`/bpm/process-instance/${encodeURIComponent(processInstanceId)}`);
  }

  /** GET /bpm/process-instance/{instanceId}/definition-xml */
  getProcessDefinitionXml(processInstanceId: string): Observable<CamundaProcessDefinitionXml> {
    return this.baseHttp.get<CamundaProcessDefinitionXml>(`/bpm/process-instance/${encodeURIComponent(processInstanceId)}/definition-xml`);
  }

  /** GET /bpm/process-instance/{instanceId}/history-activities */
  getHistoryActivityInstances(processInstanceId: string): Observable<CamundaHistoricActivityInstance[]> {
    return this.baseHttp
      .get<ArrayResult<CamundaHistoricActivityInstance>>(`/bpm/process-instance/${encodeURIComponent(processInstanceId)}/history-activities`)
      .pipe(
      map(res => res?.content ?? []),
      map(items =>
        items.map(item => {
          const completed = item.completed ?? (item.endTime != null && item.endTime !== '');
          return {
            ...item,
            completed,
            active: item.active ?? !completed
          };
        })
      )
    );
  }

  /**
   * 一键恢复运行中实例上所有 OPEN 的 incident：
   * {@code POST /bpm/process-instance/{instanceId}/recover?retries={n}}（默认 3，范围 1～100）。
   */
  recoverProcessInstance(processInstanceId: string, retries?: number): Observable<BpmInstanceRecoverResultDto> {
    const query = retries != null ? `?retries=${encodeURIComponent(String(retries))}` : '';
    return this.baseHttp.post<BpmInstanceRecoverResultDto>(`/bpm/process-instance/${encodeURIComponent(processInstanceId)}/recover${query}`, null);
  }

  /** GET /bpm/process-instance/{instanceId}/variables */
  getProcessInstanceVariables(processInstanceId: string): Observable<CamundaHistoricVariableInstance[]> {
    return this.baseHttp
      .get<ArrayResult<CamundaHistoricVariableInstance>>(`/bpm/process-instance/${encodeURIComponent(processInstanceId)}/variables`)
      .pipe(map(res => res?.content ?? []));
  }
}
