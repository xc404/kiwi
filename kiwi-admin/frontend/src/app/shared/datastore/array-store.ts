import { DataStore, DataStoreConfig } from './data-store';
import { MemoryProxy } from './proxy/memory-proxy';

export interface ArrayStoreConfig extends DataStoreConfig {
  /** 默认 0 表示一次加载全部记录 */
  pageSize?: number;
}

/**
 * 内存数组 Store，对标 ExtJS Ext.data.ArrayStore。
 * 用于静态选项、前端已持有的列表等无需远程请求的场景。
 */
export class ArrayStore<T> extends DataStore<T> {
  readonly memoryProxy: MemoryProxy<T>;

  constructor(data: T[] = [], config?: ArrayStoreConfig) {
    const memoryProxy = new MemoryProxy<T>(data);
    super(memoryProxy, {
      pageSize: 0,
      ...config
    });
    this.memoryProxy = memoryProxy;
  }

  /** 替换全部数据并刷新当前页 */
  loadData(data: T[]): void {
    this.memoryProxy.setData(data);
    this.load(this.pageIndex);
  }

  getData(): T[] {
    return this.memoryProxy.getData();
  }

  add(...records: T[]): void {
    this.memoryProxy.addRecords(records);
    this.load(this.pageIndex);
  }

  remove(predicate: (record: T) => boolean): void {
    this.memoryProxy.removeRecord(predicate);
    this.load(this.pageIndex);
  }
}
