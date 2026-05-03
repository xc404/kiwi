import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import type { BpmProcess } from '../../types/bpm-process';
import type { ComponentDescription } from '../../flow-elements/component-provider';

/** 与后端 {@code BpmProcessDefinitionCtl.SaveInput} 保存请求体一致 */
export type BpmProcessSaveBody = {
  name?: string;
  bpmnXml?: string;
  maxProcessInstances?: number;
};

@Injectable({
  providedIn: 'root',
})
export class ProcessDesignService {
  private apiUrl = '/bpm/process';

  constructor(private http: BaseHttpService) {}

  getProcessList(): Observable<unknown> {
    return this.http.get(`${this.apiUrl}/list`);
  }

  getProcessById(id: string): Observable<BpmProcess> {
    return this.http.get<BpmProcess>(`${this.apiUrl}/${id}`);
  }

  updateProcess(id: string, processData: BpmProcessSaveBody): Observable<BpmProcess> {
    return this.http.put<BpmProcess>(`${this.apiUrl}/${id}`, processData);
  }

  create(parentId: string, processName: string): Observable<BpmProcess> {
    return this.http.post<BpmProcess>(`${this.apiUrl}`, {
      name: processName,
      folderId: parentId,
    });
  }

  saveAsProcess(id: string, processName: string, xml: string): Observable<BpmProcess> {
    return this.http.post<BpmProcess>(`${this.apiUrl}/${id}`, { name: processName, bpmnXml: xml });
  }

  deleteProcess(id: string): Observable<unknown> {
    return this.http.delete(`${this.apiUrl}/${id}`);
  }

  deployProcess(id: string): Observable<BpmProcess> {
    return this.http.post<BpmProcess>(`${this.apiUrl}/${id}/deploy`, {});
  }

  validateProcess(processData: unknown): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/validate`, processData);
  }

  /**
   * 启动已部署流程；body 与后端 {@code StartProcessInput} 一致，variables 可选。
   * 响应为引擎流程实例 DTO（非 BpmProcess）。
   */
  startProcess(id: string, body?: { variables?: Record<string, unknown> }) {
    const payload =
      body?.variables !== undefined ? { variables: body.variables } : {};
    return this.http.post<unknown>(`${this.apiUrl}/${id}/start`, payload);
  }

  /** 未保存 BPMN 预览：包装为逻辑组件契约（只读分析） */
  analyzeProcessAsComponent(bpmnXml: string) {
    return this.http.post<unknown>(`${this.apiUrl}/analyze-as-component`, { bpmnXml });
  }

  /** 已保存流程：服务端按 id 分析并包装为组件契约（只读） */
  getProcessAsComponent(processId: string) {
    return this.http.get<unknown>(`${this.apiUrl}/${processId}/as-component`);
  }

  /**
   * 另存为组件：与后端 {@code SaveAsComponentInput} 一致（name、description、version）。
   * 响应为 {@code BpmComponent}（非 BpmProcess）。
   */
  saveAsComponent(
    processId: string,
    body: { name: string; description?: string; version?: string },
  ) {
    return this.http.post<unknown>(`${this.apiUrl}/${processId}/save-as-component`, body);
  }

  /**
   * 从当前用户已保存流程 BPMN 按需解析的「最近使用组件」（不落库）。
   */
  getRecentComponentUsages(): Observable<ComponentDescription[]> {
    return this.http.get<{ content: ComponentDescription[] }>('/bpm/component/recent-usage').pipe(
      map((res) => res.content),
    );
  }
}
