import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { filter, map, switchMap } from 'rxjs/operators';

import { Utils } from '@app/utils/utils';
import { successCode, warningCode } from '@config/constant';
import { environment } from '@env/environment';
import { SpinService } from '@store/common-store/spin.service';
import * as qs from 'qs';

import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { NzMessageService } from 'ng-zorro-antd/message';

export interface HttpCustomConfig {
  needSuccessInfo?: boolean; // 是否需要"操作成功"提示
  showLoading?: boolean; // 是否需要loading
  otherUrl?: boolean; // 是否是第三方接口
}

export interface ActionResult<T> {
  code: number;
  msg: string;
  data: T;
}

@Injectable({
  providedIn: 'root'
})
export class BaseHttpService {
  uri: string;
  http = inject(HttpClient);
  message = inject(NzMessageService);
  spinService = inject(SpinService);
  successCode = successCode;
  warningCode = warningCode;
  protected constructor() {
    this.uri = environment.api.baseUrl;
  }

  get<T>(path: string, param?: NzSafeAny, config?: HttpCustomConfig): Observable<T> {
    config = { needSuccessInfo: false, ...config };
    const reqPath = this.getUrl(path, config);
    const params = new HttpParams({ fromString: qs.stringify(param) });
    return this.before(config)
      .pipe(switchMap(() => this.http.get<ActionResult<T>>(reqPath, { params })))
      .pipe(this.after<T>(config));
  }

  delete<T>(path: string, param?: NzSafeAny, config?: HttpCustomConfig): Observable<T> {
    config = { needSuccessInfo: false, showLoading: true, ...config };
    const reqPath = this.getUrl(path, config);
    const params = new HttpParams({ fromString: qs.stringify(param) });
    return this.before(config)
      .pipe(switchMap(() => this.http.delete<ActionResult<T>>(reqPath, { params })))
      .pipe(this.after<T>(config));
  }

  post<T>(path: string, param?: NzSafeAny, config?: HttpCustomConfig): Observable<T> {
    config = { needSuccessInfo: false, showLoading: true, ...config };
    const reqPath = this.getUrl(path, config);
    return this.before(config)
      .pipe(switchMap(() => this.http.post<ActionResult<T>>(reqPath, param)))
      .pipe(this.after<T>(config));
  }

  put<T>(path: string, param?: NzSafeAny, config?: HttpCustomConfig): Observable<T> {
    config = { needSuccessInfo: false, showLoading: true, ...config };
    const reqPath = this.getUrl(path, config);
    return this.before(config)
      .pipe(switchMap(() => this.http.put<ActionResult<T>>(reqPath, param)))
      .pipe(this.after<T>(config));
  }

  request<T>(method: string, path: string, options?: any, config?: HttpCustomConfig): Observable<T> {
    const showLoading = method.toLocaleLowerCase() != 'get';
    config = { needSuccessInfo: false, showLoading, ...config };
    options = { ...options, observe: 'body', responseType: 'json' };
    if (method.toLocaleLowerCase() == 'get' || method.toLocaleLowerCase() == 'delete') {
      if (options.params) {
        options.params = new HttpParams({ fromString: qs.stringify(options.params) });
      }
    }
    const reqPath = this.getUrl(path, config);
    return this.before(config)
      .pipe(
        switchMap(() =>
          this.http.request<ActionResult<T>>(method, reqPath, options).pipe(
            map(res => {
              const r = res as any as ActionResult<T>;
              return r;
            })
          )
        )
      )
      .pipe(this.after<T>(config));
  }

  downLoadWithBlob(path: string, param?: NzSafeAny, config?: HttpCustomConfig): Observable<NzSafeAny> {
    config = config || { needSuccessInfo: false };
    const reqPath = this.getUrl(path, config);
    return this.http.post(reqPath, param, {
      responseType: 'blob',
      headers: new HttpHeaders().append('Content-Type', 'application/json')
    });
  }

  getUrl(path: string, config: HttpCustomConfig): string {
    let reqPath = Utils.joinUrl(this.uri, path);
    if (config.otherUrl) {
      reqPath = path;
    }
    return reqPath;
  }

  after<T>(config: HttpCustomConfig): (observable: Observable<ActionResult<T>>) => Observable<T> {
    return (observable: Observable<ActionResult<T>>) => {
      return observable.pipe(
        filter(item => {
          return this.handleFilter(item, config);
        }),
        map(item => {
          if (!this.isOkCode(item.code)) {
            throw new Error(item.msg);
          }
          return item.data;
        })
      );
    };
  }

  handleFilter<T>(item: ActionResult<T>, config: HttpCustomConfig): boolean {
    if (config.showLoading) {
      this.spinService.$globalSpinStore.set(false);
    }
    if (!this.isOkCode(item.code)) {
      this.message.error(item.msg);
    } else if (this.warningCode.includes(item.code) && item.msg?.trim()) {
      this.message.warning(item.msg.trim());
    } else if (config.needSuccessInfo) {
      this.message.success('操作成功');
    }
    return true;
  }

  private isOkCode(code: number): boolean {
    return this.successCode.includes(code) || this.warningCode.includes(code);
  }

  before(config: HttpCustomConfig): Observable<boolean> {
    if (config.showLoading) {
      this.spinService.$globalSpinStore.set(true);
      return of(true);
    }
    return of(false);
  }
}
