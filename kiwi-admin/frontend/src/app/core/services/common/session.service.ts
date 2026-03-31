import { DestroyRef, inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { TokenKey, TokenPre } from '@config/constant';
import { SimpleReuseStrategy } from '@core/services/common/reuse-strategy';
import { TabService } from '@core/services/common/tab.service';
import { WindowService } from '@core/services/common/window.service';
import { LoginService } from '@services/login/login.service';
import { MenuStoreService } from '@store/common-store/menu-store.service';
import { UserInfoStoreService } from '@store/common-store/userInfo-store.service';

/*
 * 登录/登出
 * */
@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private destroyRef = inject(DestroyRef);
  private activatedRoute = inject(ActivatedRoute);
  private tabService = inject(TabService);
  private loginService = inject(LoginService);
  private router = inject(Router);
  private userInfoService = inject(UserInfoStoreService);
  private menuService = inject(MenuStoreService);
  private windowServe = inject(WindowService);

  // 通过用户所拥有的权限码来获取菜单数组
  getUserMenus() {
    return this.loginService.getUserMenus();
  }

  setSession(token: String) {
    this.windowServe.setStorage(TokenKey, TokenPre + token);
  }

  hasSession(): boolean {
    return this.windowServe.getStorage(TokenKey) ? true : false;
  }

  getToken(): string {
    return this.windowServe.getStorage(TokenKey) as string;
  }

  refreshSession(): Promise<void> {
    if (!this.hasSession()) {
      return this.router
        .navigate(['/login/login-form'])
        .then(() => {
          return this.clearTabCash();
        })
        .then(() => {
          return this.clearSessionCash();
        });
    }
    return new Promise(resolve => {
      forkJoin(this.loginService.getUserInfo(),
        this.loginService.getUserMenus(),
        this.loginService.getUserPermissions())
        .pipe(
          finalize(() => {
            resolve();
          }),
          takeUntilDestroyed(this.destroyRef)
        ).subscribe(res => {
          let userInfo = res[0];
          let menus = res[1];
          let permissions = res[2];
          userInfo.permissions = permissions;
          this.userInfoService.$userInfo.set(userInfo);

          this.menuService.setMenuArrayStore(menus);
          resolve();
        });

    });
  }


  // 清除Tab缓存,是与路由复用相关的东西
  clearTabCash(): Promise<void> {
    return SimpleReuseStrategy.deleteAllRouteSnapshot(this.activatedRoute.snapshot).then(() => {
      return new Promise(resolve => {
        // 清空tab
        this.tabService.clearTabs();
        resolve();
      });
    });
  }

  clearSessionCash(): Promise<void> {
    return new Promise(resolve => {
      this.windowServe.removeSessionStorage(TokenKey);
      this.menuService.setMenuArrayStore([]);
      resolve();
    });
  }

  loginOut(): Promise<void> {
    this.loginService.loginOut().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    return this.router
      .navigate(['/login/login-form'])
      .then(() => {
        return this.clearTabCash();
      })
      .then(() => {
        return this.clearSessionCash();
      });
  }
}
