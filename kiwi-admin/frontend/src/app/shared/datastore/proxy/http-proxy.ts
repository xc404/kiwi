import { Observable } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';

import { DataProxy, ReadParams } from './data-proxy';

/** HTTP GET Proxy，对标 ExtJS Ajax Proxy（只读）。 */
export class HttpProxy implements DataProxy {
  constructor(
    private http: BaseHttpService,
    private url: string
  ) {}

  read(params: ReadParams): Observable<unknown> {
    return this.http.get<unknown>(this.url, params);
  }
}
