import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import type { ComponentDescription } from '../../component/component-provider';

@Injectable({
    providedIn: 'root'
})
export class ProcessDesignService {


    private apiUrl = '/bpm/process';

    constructor(private http: BaseHttpService) {

    }

    getProcessList(): Observable<any> {
        return this.http.get(`${this.apiUrl}/list`);
    }

    getProcessById(id: string): Observable<any> {
        return this.http.get(`${this.apiUrl}/${id}`);
    }


    updateProcess(id: string, processData: any): Observable<any> {
        return this.http.put(`${this.apiUrl}/${id}`, processData);
    }


    create(parentId: string, processName: string): Observable<any> {
        return this.http.post(`${this.apiUrl}`, { name: processName, folderId: parentId });
    }

    saveAsProcess(id: any, processName: any, xml: any): Observable<any> {
        return this.http.post(`${this.apiUrl}/${id}`, { name: processName, bpmnXml: xml });
    }

    deleteProcess(id: string): Observable<any> {
        return this.http.delete(`${this.apiUrl}/${id}`);
    }

    deployProcess(id: string): Observable<any> {
        return this.http.post(`${this.apiUrl}/${id}/deploy`, {});
    }

    validateProcess(processData: any): Observable<any> {
        return this.http.post(`${this.apiUrl}/validate`, processData);
    }

    startProcess(id: string) {
        return this.http.post(`${this.apiUrl}/${id}/start`, {});
    }

    /** 未保存 BPMN 预览：包装为逻辑组件契约（只读分析） */
    analyzeProcessAsComponent(bpmnXml: string) {
        return this.http.post<any>(`${this.apiUrl}/analyze-as-component`, { bpmnXml });
    }

    /** 已保存流程：服务端按 id 分析并包装为组件契约（只读） */
    getProcessAsComponent(processId: string) {
        return this.http.get<any>(`${this.apiUrl}/${processId}/as-component`);
    }

    /**
     * 另存为组件：与后端 {@code SaveAsComponentInput} 一致（name、description、version）。
     */
    saveAsComponent(
        processId: string,
        body: { name: string; description?: string; version?: string },
    ) {
        return this.http.post<any>(`${this.apiUrl}/${processId}/save-as-component`, body);
    }

    /**
     * 从当前用户已保存流程 BPMN 按需解析的「最近使用组件」（不落库）。
     * 与组件库一致为 ComponentDescription / 后端 BpmComponent，快照已合并进参数的 defaultValue；另含 lastUsedFromProcessAt（RecentBpmComponent）。
     */
    getRecentComponentUsages(): Observable<ComponentDescription[]> {
        return this.http.get<any>('/bpm/component/recent-usage')
            .pipe(map(res => {
                return res.content as ComponentDescription[];
            }));
    }
}