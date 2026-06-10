import { Injectable } from '@angular/core';

import { DataStore } from './data-store';

/**
 * 通用 Store 注册表，对标 ExtJS Ext.data.StoreManager。
 * 不绑定具体 Model / Proxy 类型；字典等特化 Store 由各自 Factory/Service 创建后 register。
 */
@Injectable({ providedIn: 'root' })
export class StoreManager {
  private readonly stores = new Map<string, DataStore<unknown>>();

  lookup<T = unknown>(storeId: string): DataStore<T> | undefined {
    return this.stores.get(storeId) as DataStore<T> | undefined;
  }

  register<T>(store: DataStore<T>): DataStore<T> {
    const storeId = store.storeId;
    if (!storeId) {
      throw new Error('StoreManager.register 要求 store 设置 storeId');
    }
    const existing = this.stores.get(storeId);
    if (existing) {
      return existing as DataStore<T>;
    }
    this.stores.set(storeId, store);
    return store;
  }

  reload(storeId: string): void {
    this.lookup(storeId)?.load(0);
  }

  unregister(storeId: string): void {
    this.stores.delete(storeId);
  }
}
