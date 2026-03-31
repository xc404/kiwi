import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, catchError, forkJoin, map, of, switchMap, throwError } from 'rxjs';

/** 流程 / 节点作用域变量（Camunda REST 运行时或历史） */
export interface ProcessVariableRow {
  name: string;
  type: string;
  value: unknown;
  /** 流程级变量或节点局部变量 */
  scope: 'process' | 'activity';
  activityInstanceId?: string | null;
}

export interface ProcessInstanceDiagramView {
  processInstanceId: string;
  processDefinitionId: string;
  processDefinitionKey: string | null;
  ended: boolean;
  suspended: boolean;
  businessKey: string | null;
  bpmnXml: string;
  activeActivityIds: string[];
  completedActivityIds: string[];
  variables: ProcessVariableRow[];
  /** BPMN activityId → Camunda activity instance id 列表，用于按选中节点过滤变量 */
  activityInstanceIdsByActivityId: Record<string, string[]>;
}

/** Camunda History Activity Instance（GET /history/activity-instance） */
interface CamundaHistoricActivityInstance {
  /** activity instance id（与 variable 上 activityInstanceId 对应） */
  id?: string;
  activityId: string | null;
  activityType: string | null;
  endTime: string | null;
}

/** runtime/history variable-instance 列表项 */
interface CamundaVariableInstanceItem {
  name: string | null;
  type?: string | null;
  value?: unknown;
  activityInstanceId?: string | null;
}

/** 流程定义 XML（GET /process-definition/{id}/xml） */
interface CamundaProcessDefinitionXml {
  bpmn20Xml: string;
}

/**
 * 与 fetchProcessInstance 结果一致，用于 fetchVariables 选择 REST 路径。
 * - runtime：当前在运行时表，走 /process-instance/{id}/variables
 * - historic：仅历史可查，直接走 /history/variable-instance
 */
export interface ProcessInstanceVariableFetchState {
  apiSource: 'runtime' | 'historic';
  ended: boolean;
  suspended: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class ProcessInstanceService {
  private readonly http = inject(HttpClient);

  /** 与 camunda-bpm-spring-boot-starter-rest 默认上下文一致 */
  private engineRestRoot(): string {
    return `${environment.api.baseUrl}${environment.api.camundaEngineRestPath}`;
  }

  /**
   * 使用 Camunda Engine REST API 聚合：流程实例元数据 + 定义 XML + 历史活动实例（当前/已完成节点）。
   */
  getDiagramView(processInstanceId: string): Observable<ProcessInstanceDiagramView> {
    const root = this.engineRestRoot();
    return this.fetchProcessInstance(root, processInstanceId).pipe(
      switchMap(({ pi, apiSource }) => {
        const processDefinitionId = (pi['definitionId'] ?? pi['processDefinitionId']) as string | undefined;
        if (!processDefinitionId) {
          return throwError(() => new Error('响应中缺少流程定义 ID'));
        }

        const variableState: ProcessInstanceVariableFetchState = {
          apiSource,
          ended: this.isEnded(pi),
          suspended: pi['suspended'] === true,
        };

        const xml$ = this.http.get<CamundaProcessDefinitionXml>(
          `${root}/process-definition/${encodeURIComponent(processDefinitionId)}/xml`,
        );
        const activities$ = this.http.get<CamundaHistoricActivityInstance[]>(
          `${root}/history/activity-instance`,
          {
            params: {
              processInstanceId,
              sortBy: 'startTime',
              sortOrder: 'asc',
            },
          },
        );
        const def$ = this.http
          .get<{ key?: string }>(`${root}/process-definition/${encodeURIComponent(processDefinitionId)}`)
          .pipe(catchError(() => of({ key: undefined })));
        const variables$ = this.fetchVariables(root, processInstanceId, variableState).pipe(
          catchError(() => of<ProcessVariableRow[]>([])),
        );

        return forkJoin({ xml: xml$, activities: activities$, def: def$, variables: variables$ }).pipe(
          map(({ xml, activities, def, variables }) =>
            this.buildView(
              processInstanceId,
              pi,
              processDefinitionId,
              def.key ?? null,
              xml.bpmn20Xml,
              activities,
              variables,
            ),
          ),
        );
      }),
    );
  }

  /**
   * 按流程实例来源拉变量：historic 只打历史接口；runtime 打运行时接口，404 时再回退历史（兼容极端情况）。
   */
  private fetchVariables(
    root: string,
    processInstanceId: string,
    state: ProcessInstanceVariableFetchState,
  ): Observable<ProcessVariableRow[]> {
    if (state.apiSource === 'historic') {
      return this.fetchHistoricVariableInstances(root, processInstanceId);
    }

    /** 运行时优先用 variable-instance，可带 activityInstanceId，便于按 BPMN 节点过滤 */
    return this.fetchRuntimeVariableInstances(root, processInstanceId).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status !== 404) {
          return throwError(() => err);
        }
        return this.fetchHistoricVariableInstances(root, processInstanceId);
      }),
    );
  }

  private fetchRuntimeVariableInstances(root: string, processInstanceId: string): Observable<ProcessVariableRow[]> {
    return this.http
      .get<CamundaVariableInstanceItem[]>(`${root}/variable-instance`, {
        params: {
          processInstanceId,
          deserializeValues: 'true',
        },
      })
      .pipe(map((list) => this.variableInstanceListToRows(list)));
  }

  private fetchHistoricVariableInstances(root: string, processInstanceId: string): Observable<ProcessVariableRow[]> {
    return this.http
      .get<CamundaVariableInstanceItem[]>(`${root}/history/variable-instance`, {
        params: {
          processInstanceId,
          // sortBy: 'createTime',
          // sortOrder: 'desc',
          deserializeValues: 'true',
        },
      })
      .pipe(map((list) => this.variableInstanceListToRows(list)));
  }

  private variableInstanceListToRows(list: CamundaVariableInstanceItem[]): ProcessVariableRow[] {
    const seen = new Set<string>();
    const rows: ProcessVariableRow[] = [];
    for (const row of list) {
      if (!row.name) {
        continue;
      }
      const key = `${row.name}\0${row.activityInstanceId ?? ''}`;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      rows.push({
        name: row.name,
        type: row.type ?? 'Unknown',
        value: row.value,
        scope: row.activityInstanceId ? 'activity' : 'process',
        activityInstanceId: row.activityInstanceId,
      });
    }
    rows.sort((a, b) => {
      const scopeOrder = a.scope === b.scope ? 0 : a.scope === 'process' ? -1 : 1;
      if (scopeOrder !== 0) {
        return scopeOrder;
      }
      return a.name.localeCompare(b.name);
    });
    return rows;
  }

  private buildActivityInstanceIndex(activities: CamundaHistoricActivityInstance[]): Record<string, string[]> {
    const index: Record<string, string[]> = {};
    for (const row of activities) {
      const aid = row.activityId;
      const instanceId = row.id ?? (row as { activityInstanceId?: string }).activityInstanceId;
      if (!aid || !instanceId || row.activityType === 'sequenceFlow') {
        continue;
      }
      if (!index[aid]) {
        index[aid] = [];
      }
      if (!index[aid].includes(instanceId)) {
        index[aid].push(instanceId);
      }
    }
    return index;
  }

  private fetchProcessInstance(
    root: string,
    id: string,
  ): Observable<{ pi: Record<string, unknown>; apiSource: 'runtime' | 'historic' }> {
    return this.http.get<Record<string, unknown>>(`${root}/process-instance/${encodeURIComponent(id)}`).pipe(
      map((pi) => ({ pi, apiSource: 'runtime' as const })),
      catchError((err: HttpErrorResponse & { code?: number }) => {
        const status = err.status ?? err.code;
        if (status === 404) {
          return this.http
            .get<Record<string, unknown>>(`${root}/history/process-instance/${encodeURIComponent(id)}`)
            .pipe(map((pi) => ({ pi, apiSource: 'historic' as const })));
        }
        return throwError(() => err);
      }),
    );
  }

  private buildView(
    processInstanceId: string,
    pi: Record<string, unknown>,
    processDefinitionId: string,
    processDefinitionKey: string | null,
    bpmnXml: string,
    activities: CamundaHistoricActivityInstance[],
    variables: ProcessVariableRow[],
  ): ProcessInstanceDiagramView {
    const ended = this.isEnded(pi);
    const suspended = pi['suspended'] === true;

    const activeSet = new Set<string>();
    const completedSet = new Set<string>();

    for (const row of activities) {
      const aid = row.activityId;
      if (!aid || row.activityType === 'sequenceFlow') {
        continue;
      }
      if (row.endTime == null || row.endTime === '') {
        activeSet.add(aid);
      } else {
        completedSet.add(aid);
      }
    }

    for (const aid of activeSet) {
      completedSet.delete(aid);
    }

    return {
      processInstanceId,
      processDefinitionId,
      processDefinitionKey,
      ended,
      suspended,
      businessKey: (pi['businessKey'] as string) ?? null,
      bpmnXml,
      activeActivityIds: [...activeSet],
      completedActivityIds: [...completedSet],
      variables,
      activityInstanceIdsByActivityId: this.buildActivityInstanceIndex(activities),
    };
  }

  private isEnded(pi: Record<string, unknown>): boolean {
    if (pi['ended'] === true) {
      return true;
    }
    const endTime = pi['endTime'];
    return endTime != null && endTime !== '';
  }
}
