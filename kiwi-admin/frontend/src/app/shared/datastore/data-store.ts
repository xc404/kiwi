import { signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { Page } from './page';
import { DataProxy, ReadParams } from './proxy/data-proxy';
import { DefaultPageReader, PageReaderConfig, readPage } from './reader/page-reader';

export interface DataStoreConfig {
  storeId?: string;
  reader?: PageReaderConfig;
  pageSize?: number;
  sortParams?: string;
  basicParams?: Record<string, unknown>;
  autoLoad?: boolean;
  /** 分页加载时追加到已有记录（字典下拉滚动加载） */
  appendMode?: boolean;
}

/**
 * 通用远程 Store，对标 ExtJS Ext.data.Store。
 * 通过 Proxy 读数据、Reader 解析响应，不依赖 CRUD。
 */
export class DataStore<T> {
  readonly storeId?: string;
  readonly reader: PageReaderConfig;

  items = signal<Page<T>>(new Page<T>());
  loading = signal(false);

  pageSize = 10;
  pageIndex = 0;
  sortParams?: string;
  basicParams: Record<string, unknown> = {};
  currentParams?: Record<string, unknown>;
  appendMode = false;

  private loadSub?: Subscription;

  constructor(
    private proxy: DataProxy,
    config?: DataStoreConfig
  ) {
    this.storeId = config?.storeId;
    this.reader = config?.reader ?? DefaultPageReader;
    if (config?.pageSize != null) {
      this.pageSize = config.pageSize;
    }
    if (config?.sortParams != null) {
      this.sortParams = config.sortParams;
    }
    if (config?.basicParams != null) {
      this.basicParams = { ...config.basicParams };
    }
    if (config?.appendMode != null) {
      this.appendMode = config.appendMode;
    }
    if (config?.autoLoad) {
      this.load();
    }
  }

  load(pageIndex = 0, pageSize?: number): void {
    this.page(pageIndex, pageSize);
  }

  reload(): void {
    this.doLoad();
  }

  page(pageIndex: number, pageSize?: number): void {
    this.pageIndex = pageIndex;
    if (pageSize != null) {
      this.pageSize = pageSize;
    }
    this.doLoad();
  }

  nextPage(): void {
    this.page(this.pageIndex + 1);
  }

  prePage(): void {
    this.page(this.pageIndex - 1);
  }

  noResults(): boolean {
    const results = this.items();
    return !results || results.length === 0;
  }

  records(): T[] {
    return Array.from(this.items());
  }

  findRecord<K extends keyof T>(field: K, value: T[K]): T | undefined {
    return this.records().find(record => record[field] === value);
  }

  protected buildReadParams(): ReadParams {
    const params: ReadParams = {
      ...this.basicParams,
      ...(this.currentParams ?? {})
    };
    if (this.pageIndex >= 0) {
      params.page = this.pageIndex;
    }
    if (this.pageSize > 0) {
      params.size = this.pageSize;
    }
    if (this.sortParams) {
      params.sort = this.sortParams;
    }
    return params;
  }

  protected doLoad(): void {
    this.loadSub?.unsubscribe();
    const previousItems = this.appendMode && this.pageIndex > 0 ? this.records() : [];
    this.loading.set(true);
    this.loadSub = this.proxy
      .read(this.buildReadParams())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response: unknown) => {
          const { items, totalCount } = readPage<T>(response, this.reader);
          const merged = this.appendMode && this.pageIndex > 0 ? [...previousItems, ...items] : items;
          const total = Math.max(totalCount, merged.length);
          this.items.set(new Page(this.pageIndex, this.pageSize, total, merged));
        },
        error: () => {
          this.items.set(new Page(this.pageIndex, this.pageSize, 0, []));
        }
      });
  }
}
