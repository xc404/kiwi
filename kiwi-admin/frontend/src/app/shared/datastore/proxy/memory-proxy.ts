import { Observable, of } from 'rxjs';

import { DataProxy, ReadParams } from './data-proxy';

/**
 * 内存 Proxy，对标 ExtJS Memory Proxy。
 * 数据保存在客户端，read 支持本地分页切片。
 */
export class MemoryProxy<T> implements DataProxy {
  constructor(private data: T[] = []) {}

  read(params: ReadParams): Observable<unknown> {
    const total = this.data.length;
    const page = typeof params.page === 'number' && params.page >= 0 ? params.page : 0;
    const size = typeof params.size === 'number' && params.size > 0 ? params.size : 0;
    const items = size > 0 ? this.data.slice(page * size, (page + 1) * size) : [...this.data];
    return of({
      content: items,
      page: { totalElements: total }
    });
  }

  getData(): T[] {
    return [...this.data];
  }

  setData(data: T[]): void {
    this.data = [...data];
  }

  addRecords(records: T[]): void {
    this.data = [...this.data, ...records];
  }

  removeRecord(predicate: (record: T) => boolean): void {
    this.data = this.data.filter(record => !predicate(record));
  }
}
