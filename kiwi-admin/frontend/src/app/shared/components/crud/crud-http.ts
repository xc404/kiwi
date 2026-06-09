import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { Utils } from '@app/utils/utils';

export enum HttpMethod {
  GET = 'GET',
  POST = 'POST',
  PUT = 'PUT',
  DELETE = 'DELETE',
  PATCH = 'PATCH'
}

export interface CrudHttpConfig {
  method?: HttpMethod;
  url: string;
  responseType?: 'json';
  observe?: 'body';
  permission?: string;
}

export interface CrudHttpConfigs {
  get?: CrudHttpConfig;
  create?: CrudHttpConfig;
  update?: CrudHttpConfig;
  delete?: CrudHttpConfig;
  search?: CrudHttpConfig;
}

export function crudConfig(
  config:
    | string
    | CrudHttpConfig
    | {
        get?: string | CrudHttpConfig | boolean;
        create?: string | CrudHttpConfig | boolean;
        update?: string | CrudHttpConfig | boolean;
        delete?: string | CrudHttpConfig | boolean;
        search?: string | CrudHttpConfig | boolean;
      }
): CrudHttpConfigs {
  const c: any = config;
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
    get: copyConfig(c.get, { ...defaultConfig, method: HttpMethod.GET, permission: getPermission(defaultConfig.permission, 'view') }),
    create: copyConfig(c.create, { ...defaultConfig, method: HttpMethod.POST, permission: getPermission(defaultConfig.permission, 'edit') }),
    update: copyConfig(c.update, { ...defaultConfig, method: HttpMethod.PUT, permission: getPermission(defaultConfig.permission, 'edit') }),
    delete: copyConfig(c.delete, { ...defaultConfig, method: HttpMethod.DELETE, permission: getPermission(defaultConfig.permission, 'del') }),
    search: copyConfig(c.search, { ...defaultConfig, method: HttpMethod.GET, permission: getPermission(defaultConfig.permission, 'view') })
  };
}

export class CrudHttp {
  initConfig: CrudHttpConfigs;

  constructor(
    private http: BaseHttpService,
    config:
      | string
      | CrudHttpConfig
      | {
          get?: string | CrudHttpConfig | boolean;
          create?: string | CrudHttpConfig | boolean;
          update?: string | CrudHttpConfig | boolean;
          delete?: string | CrudHttpConfig | boolean;
          search?: string | CrudHttpConfig | boolean;
        }
  ) {
    this.initConfig = crudConfig(config);
  }

  get(id: string | number): Observable<HttpResponse<object>> {
    const config = this.initConfig.get;
    if (!config) {
      throw 'get config is null';
    }
    const url = Utils.joinUrl(config.url, `${id}`);
    const method = config.method as string;
    return this.http.request(method, url, config);
  }

  delete(id: string | number): Observable<HttpResponse<object>> {
    const deleteConfig = this.initConfig.delete;
    if (!deleteConfig) {
      throw 'delete config is null';
    }
    const url = Utils.joinUrl(deleteConfig.url, `${id}`);
    const method = deleteConfig.method as string;
    return this.http.request(method, url, deleteConfig);
  }

  create(data: any): Observable<HttpResponse<object>> {
    const createConfig = this.initConfig.create;
    if (!createConfig) {
      throw 'create config is null';
    }
    const url = createConfig.url;
    const option = { body: data, ...createConfig };
    return this.http.request(createConfig.method as string, url, option);
  }

  update(data: any, id: string | number): Observable<HttpResponse<object>> {
    const updateConfig = this.initConfig.update;
    if (!updateConfig) {
      throw 'update config is null';
    }
    let url = updateConfig.url;
    if (id !== undefined && id !== null && id !== '') {
      url = `${updateConfig.url}/${id}`;
    }
    const method = updateConfig.method as string;
    const option: { responseType: 'json'; observe: 'response'; body: any } = { responseType: 'json', observe: 'response', body: { ...data, id }, ...updateConfig } as any;
    return this.http.request(method, url, option);
  }

  search(params: any): Observable<HttpResponse<any>> {
    const searchConfig = this.initConfig.search;
    if (!searchConfig) {
      throw 'search config is null';
    }
    const url = searchConfig.url;
    const method = searchConfig.method as string;
    const option: { responseType: 'json'; observe: 'response'; params: any } = { params: params, ...searchConfig } as any;
    return this.http.request(method, url, option);
  }
}
