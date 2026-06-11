import { inject, Injectable } from '@angular/core';

import { BaseHttpService } from '@app/core/services/http/base-http.service';

import { DictStore, DictStoreConfig } from './dict-store';
import { DictRecord } from './model/dict-record';
import { StoreManager } from './store-manager';

export interface DictStorePageConfig {
  stores?: DictStoreConfig[];
  fields?: Array<{ dictKey?: string }>;
}

/**
 * 从 PageConfig 合并字典 Store 配置。
 * fields[].dictKey 默认 autoLoad: true；PageConfig.stores 显式项覆盖同 storeId 的配置。
 */
export function collectDictStoreConfigs(config: DictStorePageConfig): DictStoreConfig[] {
  const byId = new Map<string, DictStoreConfig>();

  config.fields?.forEach(field => {
    if (field.dictKey && !byId.has(field.dictKey)) {
      byId.set(field.dictKey, { storeId: field.dictKey, autoLoad: true });
    }
  });

  config.stores?.forEach(item => {
    if (!item.storeId) {
      return;
    }
    const existing = byId.get(item.storeId);
    byId.set(item.storeId, { ...existing, ...item, storeId: item.storeId });
  });

  return [...byId.values()];
}

/** @deprecated 使用 {@link collectDictStoreConfigs} */
export function collectDictStoreIds(config: DictStorePageConfig): string[] {
  return collectDictStoreConfigs(config).map(item => item.storeId);
}

/** 字典 Store 工厂，负责创建 DictStore 并注册到 StoreManager。 */
@Injectable({ providedIn: 'root' })
export class DictStoreService {
  private readonly http = inject(BaseHttpService);
  private readonly storeManager = inject(StoreManager);

  getStore(config: DictStoreConfig): DictStore {
    const existing = this.storeManager.lookup<DictRecord>(config.storeId);
    if (existing) {
      const store = existing as DictStore;
      this.ensureLoaded(store, config.autoLoad);
      return store;
    }
    const store = new DictStore(this.http, config);
    this.storeManager.register(store);
    return store;
  }

  registerStores(configs: DictStoreConfig[]): void {
    configs.forEach(config => this.getStore(config));
  }

  reload(storeId: string): void {
    this.storeManager.reload(storeId);
  }

  private ensureLoaded(store: DictStore, autoLoad?: boolean): void {
    if (autoLoad && store.records().length === 0 && !store.loading()) {
      store.load();
    }
  }
}
