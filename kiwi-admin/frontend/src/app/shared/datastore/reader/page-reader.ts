/** 分页 API 响应读取配置，对标 ExtJS Reader。 */
export interface PageReaderConfig {
  itemsProp: string;
  totalProp: string;
}

export const DefaultPageReader: PageReaderConfig = {
  itemsProp: 'content',
  totalProp: 'page.totalElements'
};

/** 支持 `a.b.c` 点路径。 */
export function getByPath(obj: unknown, path: string, defaultValue: unknown): unknown {
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

export function readPage<T>(response: unknown, reader: PageReaderConfig): { items: T[]; totalCount: number } {
  const page = getByPath(response, reader.itemsProp, []) as T[];
  const items = Array.isArray(page) ? page : [];
  let totalCount = getByPath(response, reader.totalProp, items.length) as number;
  if (typeof totalCount !== 'number' || Number.isNaN(totalCount)) {
    totalCount = items.length;
  }
  totalCount = Math.max(totalCount, items.length);
  return { items, totalCount };
}
