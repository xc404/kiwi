import { signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
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

/** 支持 `a.b.c` 点路径，用于可配置的 API 响应路径。 */
function getByPath(obj: unknown, path: string, defaultValue: unknown): unknown {
  if (obj == null || !path) {
    return defaultValue;
  }
  const keys = path.split('.');
  let cur: unknown = obj;
  for (const k of keys) {
    if (cur == null || typeof cur !== 'object') {
      return defaultValue;
    }
    cur = (cur as Record<string, unknown>)[k];
  }
  return cur ?? defaultValue;
}

export class Page<T> extends Array<T> {
  public static readonly DEFAULT_PAGE_SIZE: number = 10;

  pageIndex: number;
  pageSize: number;
  totalCount: number;

  constructor(pgIx?: number, pgSize?: number, tot?: number, items?: T[]) {
    super();
    this.pageIndex = pgIx ? pgIx : 0;
    this.pageSize = pgSize ? pgSize : 0;
    this.totalCount = tot ? tot : 0;
    if (items && items.length > 0) {
      this.push(...items);
    }
  }
}

export class CrudDataSource<T> {
  items = signal<Page<T>>(new Page<T>());

  totalProp = 'page.totalElements';
  itemsProp = 'content';
  pageSize = 10;
  pageIndex = 0;
  sortParams?: string;
  basicParams: Record<string, unknown> = {};
  currentParams?: Record<string, unknown> | undefined = undefined;
  loading = signal<boolean>(false);

  private _loadSub?: Subscription;

  constructor(
    private crud: CrudHttp,
    config?: {
      totalProp?: string;
      itemsProp?: string;
      sortParams?: string;
      pageSize?: number;
      basicParams?: Record<string, unknown>;
    }
  ) {
    if (config?.totalProp != null) {
      this.totalProp = config.totalProp;
    }
    if (config?.itemsProp != null) {
      this.itemsProp = config.itemsProp;
    }
    if (config?.pageSize != null) {
      this.pageSize = config.pageSize;
    }
    if (config?.sortParams != null) {
      this.sortParams = config.sortParams;
    }
    if (config?.basicParams != null) {
      this.basicParams = { ...config.basicParams };
    }
  }

  noResults(): boolean {
    const results = this.items();
    return !results || results.length === 0;
  }

  getItems() {
    return this.items();
  }

  private _load() {
    this._loadSub?.unsubscribe();
    const params: any = {
      ...this.basicParams,
      ...(this.currentParams ?? {}),
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
    this.loading.set(true);
    this._loadSub = this.crud
      .search(params)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response: unknown) => {
          const page = getByPath(response, this.itemsProp, []) as T[];
          const items = Array.isArray(page) ? page : [];
          let totalCount = getByPath(response, this.totalProp, items.length) as number;
          if (typeof totalCount !== 'number' || Number.isNaN(totalCount)) {
            totalCount = items.length;
          }
          totalCount = Math.max(totalCount, items.length);
          this.items.set(new Page(this.pageIndex, this.pageSize, totalCount, items));
        },
        error: () => {
          this.items.set(new Page(this.pageIndex, this.pageSize, 0, []));
        },
      });
  }

  search(params: NzTableQueryParams | Record<string, unknown>) {
    if (
      params &&
      typeof params === 'object' &&
      'pageIndex' in params &&
      params.pageIndex != null &&
      'filter' in params &&
      (params as NzTableQueryParams).filter
    ) {
      const p = params as NzTableQueryParams;
      this.pageIndex = p.pageIndex - 1;
      this.pageSize = p.pageSize;
      this.currentParams = p.filter as any;
      this.sortParams = (p.sort ?? [])
        .map(item => `${item.key} ${toSortDirection(item.value)}`)
        .join(',');
    } else {
      this.currentParams = params as Record<string, unknown>;
    }
    this._load();
  }

  reload() {
    return this._load();
  }

  sort(sortParams: string) {
    this.sortParams = sortParams;
    return this._load();
  }

  page(pageIndex: number, pageSize?: number) {
    this.pageIndex = pageIndex;
    if (pageSize) {
      this.pageSize = pageSize;
    }
    return this._load();
  }

  nextPage() {
    return this.page(this.pageIndex + 1);
  }

  prePage() {
    return this.page(this.pageIndex - 1);
  }
}
