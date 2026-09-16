import { assertInInjectionContext, inject } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot, CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { SessionService } from '../session.service';
import { WindowService } from '../window.service';

// 有兴趣的可以看看 class 与 fn 的争议 https://github.com/angular/angular/pull/47924
// 也可以去官网查找 mapToCanActivate。路由守卫：没有会话则跳转登录页
const canActivateChildFn: CanActivateFn = () => {
  // 这个方法可以检查inject是否在context中
  assertInInjectionContext(canActivateChildFn);
  const _windowSrc = inject(WindowService);
  const router = inject(Router);
  const sessionService = inject(SessionService);

  if (sessionService.hasSession()) {
    return true;
  }
  return router.parseUrl('/login');
};

export const JudgeLoginGuardActivateChild: CanActivateChildFn = (childRoute: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return canActivateChildFn(childRoute, state);
};

export const JudgeLoginGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return canActivateChildFn(route, state);
};
