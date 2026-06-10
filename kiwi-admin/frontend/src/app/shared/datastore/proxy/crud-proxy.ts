import { Observable } from 'rxjs';

import { CrudHttp } from '@app/shared/components/crud/crud-http';

import { DataProxy, ReadParams } from './data-proxy';

/**
 * CRUD Proxy，对标带 REST 写能力的 Ajax Proxy。
 * read 映射为 search；其余写操作供 CrudDataSource / CrudPage 使用。
 */
export class CrudProxy implements DataProxy {
  constructor(readonly crud: CrudHttp) {}

  read(params: ReadParams): Observable<unknown> {
    return this.crud.search(params);
  }

  get(id: string | number): Observable<unknown> {
    return this.crud.get(id);
  }

  create(data: unknown): Observable<unknown> {
    return this.crud.create(data);
  }

  update(data: unknown, id: string | number): Observable<unknown> {
    return this.crud.update(data, id);
  }

  delete(id: string | number): Observable<unknown> {
    return this.crud.delete(id);
  }
}
