import { DataStore, DataStoreConfig } from '@app/shared/datastore/data-store';
import { Page } from '@app/shared/datastore/page';
import { CrudProxy } from '@app/shared/datastore/proxy/crud-proxy';

import { NzTableQueryParams, NzTableSortOrder } from 'ng-zorro-antd/table';

import { CrudHttp } from './crud-http';

function toSortDirection(value: NzTableSortOrder): string {
  if (value == 'ascend') {
    return 'asc';
  }
  if (value == 'descend') {
    return 'desc';
  }
  return value as string;
}

export { Page };

/**
 * CRUD 列表 Store，对标 ExtJS 配置了 REST Proxy 的 Store。
 * 在通用 DataStore 之上增加表格查询、排序等 CRUD 页语义。
 */
export class CrudDataSource<T> extends DataStore<T> {
  readonly crudProxy: CrudProxy;

  constructor(crud: CrudHttp, config?: Omit<DataStoreConfig, 'autoLoad'> & { autoLoad?: boolean }) {
    const crudProxy = new CrudProxy(crud);
    super(crudProxy, config);
    this.crudProxy = crudProxy;
  }

  getItems() {
    return this.items();
  }

  search(params: NzTableQueryParams | Record<string, unknown>) {
    if (params && typeof params === 'object' && 'pageIndex' in params && params.pageIndex != null && 'filter' in params && (params as NzTableQueryParams).filter) {
      const p = params as NzTableQueryParams;
      this.pageIndex = p.pageIndex - 1;
      this.pageSize = p.pageSize;
      this.currentParams = p.filter as unknown as Record<string, unknown>;
      this.sortParams = (p.sort ?? []).map(item => `${item.key} ${toSortDirection(item.value)}`).join(',');
    } else {
      this.currentParams = params as Record<string, unknown>;
    }
    this.doLoad();
  }

  sort(sortParams: string) {
    this.sortParams = sortParams;
    this.doLoad();
  }
}
