import { HttpResponse } from "@angular/common/http";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { Utils } from "@app/utils/utils";
import { Observable } from "rxjs";


export enum HttpMethod {
    GET = "GET",
    POST = "POST",
    PUT = "PUT",
    DELETE = "DELETE",
    PATCH = "PATCH"
}

export interface CrudHttpConfig {
    method?: HttpMethod;
    url: string;
    responseType?: 'json';
    observe?: 'body'
    "permission"?: string;
}


export type CrudHttpConfigs = {
    get?: CrudHttpConfig;
    create?: CrudHttpConfig;
    update?: CrudHttpConfig;
    delete?: CrudHttpConfig;
    search?: CrudHttpConfig;
}

export function crudConfig(config: string | CrudHttpConfig | {
    get?: string | CrudHttpConfig|boolean;
    create?: string | CrudHttpConfig|boolean;
    update?: string | CrudHttpConfig|boolean;
    delete?: string | CrudHttpConfig|boolean;
    search?: string | CrudHttpConfig|boolean;
}): CrudHttpConfigs {
    let c: any = config;
    let defaultConfig: CrudHttpConfig = { url: '', responseType: 'json', observe: 'body' };
    if (typeof config === 'string') {
        defaultConfig.url = config;
    } else {

        defaultConfig = Object.assign(defaultConfig, { url: c.url, responseType: c.responseType, observe: c.observe, permission: c.permission });
    }
    function copyConfig(config: CrudHttpConfig | any | string, defaultConfig: CrudHttpConfig) {
        if (config === false) {
            return undefined;
        }
        if (typeof config === 'string') {
            return Object.assign(defaultConfig, { url: config, responseType: 'json', observe: 'body' });
        } else {
            return Object.assign(defaultConfig, config);
        }
    }

    function getPermission(prefix?: string, suffix?: string) {
        if (!prefix) {
            return undefined;
        }
        if (!prefix.endsWith(':')) {
            return prefix;
        }
        return prefix + suffix;
    }

    return {
        get: copyConfig(c.get, { ...defaultConfig, method: HttpMethod.GET, permission: getPermission(defaultConfig.permission, "view") }),
        create: copyConfig(c.create, { ...defaultConfig, method: HttpMethod.POST, permission: getPermission(defaultConfig.permission, "edit") }),
        update: copyConfig(c.update, { ...defaultConfig, method: HttpMethod.PUT, permission: getPermission(defaultConfig.permission, "edit") }),
        delete: copyConfig(c.delete, { ...defaultConfig, method: HttpMethod.DELETE, permission: getPermission(defaultConfig.permission, "del") }),
        search: copyConfig(c.search, { ...defaultConfig, method: HttpMethod.GET, permission: getPermission(defaultConfig.permission, "view") }),
    }
}

export class CrudHttp {

    initConfig: CrudHttpConfigs;

    constructor(private http: BaseHttpService, config: string | CrudHttpConfig | {
        get?: string | CrudHttpConfig|boolean;
        create?: string | CrudHttpConfig|boolean;
        update?: string | CrudHttpConfig|boolean;
        delete?: string | CrudHttpConfig|boolean;
        search?: string | CrudHttpConfig|boolean;
    }) {
        this.initConfig = crudConfig(config);
    }

    get(id: string | number): Observable<HttpResponse<Object>> {
        let config = this.initConfig.get;
        if (!config) {
            throw "get config is null";
        }
        let url = Utils.joinUrl(config.url, id + "");
        let method = config.method as string;
        return this.http.request(method, url, config);
    }

    delete(id: string | number): Observable<HttpResponse<Object>> {
        let deleteConfig = this.initConfig.delete;
        if (!deleteConfig) {
            throw "delete config is null";
        }
        let url = Utils.joinUrl(deleteConfig.url, id + "");
        let method = deleteConfig.method as string;
        return this.http.request(method, url, deleteConfig);
    }

    create(data: any): Observable<HttpResponse<Object>> {
        let createConfig = this.initConfig.create;
        if (!createConfig) {
            throw "create config is null";
        }
        let url = createConfig.url;
        let option = Object.assign({ body: data }, createConfig);
        return this.http.request(createConfig.method as string, url, option);
    }

    update(data: any, id: string | number): Observable<HttpResponse<Object>> {
        let updateConfig = this.initConfig.update;
        if (!updateConfig) {
            throw "update config is null";
        }
        let url = updateConfig.url;
        if (id !== undefined && id !== null && id !== '') {
            url = `${updateConfig.url}/${id}`;
        }
        let method = updateConfig.method as string;
        let option: { responseType: 'json', observe: 'response', body: any }
            = Object.assign({ responseType: 'json', observe: 'response', body: {...data,id} }, updateConfig) as any;
        return this.http.request(method, url, option);
    }

    search(params: any): Observable<HttpResponse<any>> {
        let searchConfig = this.initConfig.search;
        if (!searchConfig) {
            throw "search config is null";
        }
        let url = searchConfig.url;
        let method = searchConfig.method as string;
        let option: { responseType: 'json', observe: 'response', params: any } = Object.assign({ params: params }, searchConfig) as any;
        return this.http.request(method, url, option);
    }
}