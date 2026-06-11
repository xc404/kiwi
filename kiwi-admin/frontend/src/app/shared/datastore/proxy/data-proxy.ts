import { Observable } from 'rxjs';

/** Proxy 读请求参数。 */
export interface ReadParams {
  page?: number;
  size?: number;
  sort?: string;
  [key: string]: unknown;
}

/**
 * 数据 Proxy 抽象，对标 ExtJS Ext.data.proxy.Proxy。
 * Store 仅依赖 read；写操作由特化 Proxy（如 CrudProxy）扩展。
 */
export interface DataProxy {
  read(params: ReadParams): Observable<unknown>;
}
