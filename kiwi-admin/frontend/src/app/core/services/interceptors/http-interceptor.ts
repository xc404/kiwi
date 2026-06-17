import { HttpErrorResponse, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, filter } from 'rxjs/operators';

import { TokenKey } from '@config/constant';
import { WindowService } from '@core/services/common/window.service';
import { ActionResult } from '@core/services/http/base-http.service';

import { SessionService } from '../common/session.service';

interface CustomHttpConfig {
  headers?: HttpHeaders;
}

interface HttpClientError {
  code: number;
  message: string;
}

function extractBackendPayload(error: HttpErrorResponse): Partial<ActionResult<unknown>> | null {
  const body = error.error;
  if (body && typeof body === 'object' && ('msg' in body || 'code' in body)) {
    return body as Partial<ActionResult<unknown>>;
  }
  return null;
}

function defaultMessageForStatus(status: number): string {
  if (status === 0) {
    return '网络出现未知的错误，请检查您的网络。';
  }
  if (status >= 300 && status < 400) {
    return `请求被服务器重定向，状态码为${status}`;
  }
  if (status >= 400 && status < 500) {
    return `客户端出错，可能是发送的数据有误，状态码为${status}`;
  }
  if (status >= 500) {
    return `服务器发生错误，状态码为${status}`;
  }
  return '请求失败';
}

function handleError(error: HttpErrorResponse): Observable<never> {
  const backend = extractBackendPayload(error);
  const backendMsg = backend?.msg?.trim();
  const errMsg = backendMsg || defaultMessageForStatus(error.status);
  const code = typeof backend?.code === 'number' ? backend.code : error.status;

  return throwError(
    (): HttpClientError => ({
      code,
      message: errMsg
    })
  );
}

export const httpInterceptorService: HttpInterceptorFn = (req, next) => {
  const _windowServe = inject(WindowService);
  const sessionService = inject(SessionService);
  const token = sessionService.getToken();
  let httpConfig: CustomHttpConfig = {};
  if (token) {
    httpConfig = { headers: req.headers.set(TokenKey, token) };
  }
  const copyReq = req.clone(httpConfig);
  return next(copyReq).pipe(
    filter(e => e.type !== 0),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        return handleError(error);
      }
      return throwError(
        (): HttpClientError => ({
          code: -1,
          message: '请求失败'
        })
      );
    })
  );
};
