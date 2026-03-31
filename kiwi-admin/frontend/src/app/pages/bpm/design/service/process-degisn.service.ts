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
    
}