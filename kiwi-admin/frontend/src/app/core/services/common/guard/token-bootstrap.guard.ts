import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { catchError, firstValueFrom, forkJoin, map, of, tap } from 'rxjs';

import { TokenKey, TokenPre } from '@config/constant';
import { environment } from '@env/environment';
import { LoginService } from '@services/login/login.service';
import { MenuStoreService } from '@store/common-store/menu-store.service';
import { UserInfoStoreService } from '@store/common-store/userInfo-store.service';

import { SessionService } from '../session.service';
import { WindowService } from '../window.service';

const DEFAULT_TARGET = environment.postLoginPath;

/** 去掉 URL 中可能携带的 Bearer 前缀，setSession 会统一加上 TokenPre。 */
function normalizeSaToken(raw: string): string {
  const decoded = decodeURIComponent(raw);
  if (decoded.startsWith(TokenPre)) {
    return decoded.slice(TokenPre.length);
  }
  if (decoded.startsWith('Bearer ')) {
    return decoded.slice('Bearer '.length);
  }
  return decoded;
}

/** 从 /T/:token/... 解析目标路径（含前导 /）。 */
function resolveTargetPath(url: string, token: string): string {
  const prefix = `/T/${encodeURIComponent(token)}`;
  const prefixDecoded = `/T/${token}`;
  let rest = '';
  if (url.startsWith(prefixDecoded)) {
    rest = url.slice(prefixDecoded.length);
  } else if (url.startsWith(prefix)) {
    rest = url.slice(prefix.length);
  } else {
    const match = url.match(/^\/T\/([^/]+)(\/.*)?$/);
    if (match) {
      rest = match[2] ?? '';
    }
  }
  if (!rest || rest === '/') {
    return DEFAULT_TARGET;
  }
  return rest.startsWith('/') ? rest : `/${rest}`;
}

function toUrlTree(router: Router, targetPath: string): UrlTree {
  const segments = targetPath.split('/').filter(Boolean);
  return router.createUrlTree(segments);
}

/**
 * 外部深链：/#/T/{saToken}/{目标路径}
 * 写入 Session 后校验 token，replaceUrl 跳转到目标页（不在地址栏保留 token）。
 */
export const TokenBootstrapGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const sessionService = inject(SessionService);
  const loginService = inject(LoginService);
  const userInfoService = inject(UserInfoStoreService);
  const menuService = inject(MenuStoreService);
  const windowServe = inject(WindowService);

  const tokenParam = route.paramMap.get('token');
  if (!tokenParam?.trim()) {
    return router.parseUrl('/login/login-form');
  }

  const saToken = normalizeSaToken(tokenParam.trim());
  if (!saToken) {
    return router.parseUrl('/login/login-form');
  }

  sessionService.setSession(saToken);
  const targetPath = resolveTargetPath(state.url, tokenParam);

  return firstValueFrom(
    forkJoin([loginService.getUserInfo(), loginService.getUserMenus(), loginService.getUserPermissions()]).pipe(
      tap(([userInfo, menus, permissions]) => {
        userInfo.permissions = permissions;
        userInfoService.$userInfo.set(userInfo);
        menuService.setMenuArrayStore(menus);
      }),
      map(() => toUrlTree(router, targetPath)),
      catchError(() => {
        windowServe.removeStorage(TokenKey);
        menuService.setMenuArrayStore([]);
        userInfoService.$userInfo.set({ id: -1, userName: '', roles: [], permissions: [] });
        return of(router.parseUrl('/login/login-form'));
      })
    )
  ).then(urlTree => {
    return router.navigateByUrl(urlTree, { replaceUrl: true }).then(() => false);
  });
};
