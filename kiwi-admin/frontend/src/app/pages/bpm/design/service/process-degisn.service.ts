import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BaseHttpService } from '@app/core/services/http/base-http.service';

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
       return this.http.post(`${this.apiUrl}`, { name:processName, folderId: parentId});
    }
       
    saveAsProcess(id: any, processName: any, xml: any): Observable<any> {
       return this.http.post(`${this.apiUrl}/${id}`, { name:processName, bpmnXml: xml });
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
}