import { NgTemplateOutlet } from '@angular/common';
import { Component, ChangeDetectionStrategy, inject, computed, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, of, take } from 'rxjs';

import { SessionService } from '@app/core/services/common/session.service';
import { NotificationsService } from '@app/pages/personal/notifications/notifications.service';
import { WindowService } from '@core/services/common/window.service';
import { environment } from '@env/environment';
import { AccountService, UserPsd } from '@services/system/account.service';
import { ScreenLessHiddenDirective } from '@shared/directives/screen-less-hidden.directive';
import { ToggleFullscreenDirective } from '@shared/directives/toggle-fullscreen.directive';
import { UserInfoStoreService } from '@store/common-store/userInfo-store.service';
import { ModalBtnStatus } from '@widget/base-modal';
import { ChangePasswordService } from '@widget/biz-widget/change-password/change-password.service';
import { LockWidgetService } from '@widget/common-widget/lock-widget/lock-widget.service';
import { SearchRouteService } from '@widget/common-widget/search-route/search-route.service';

import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageService } from 'ng-zorro-antd/message';
import { ModalOptions } from 'ng-zorro-antd/modal';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

import { HomeNoticeComponent } from '../home-notice/home-notice.component';

@Component({
  selector: 'app-layout-head-right-menu',
  templateUrl: './layout-head-right-menu.component.html',
  styleUrls: ['./layout-head-right-menu.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, NzTooltipModule, NzIconModule, NzButtonModule, ToggleFullscreenDirective, NzDropDownModule, NzBadgeModule, NzMenuModule, HomeNoticeComponent, ScreenLessHiddenDirective]
})
export class LayoutHeadRightMenuComponent implements OnInit {
  user!: UserPsd;

  /** 铃铛徽标：未读条数，来自 GET /notifications */
  readonly unreadBadgeCount = signal(0);

  private router = inject(Router);
  private changePasswordModalService = inject(ChangePasswordService);
  private loginOutService = inject(SessionService);
  private lockWidgetService = inject(LockWidgetService);
  private windowServe = inject(WindowService);
  private searchRouteService = inject(SearchRouteService);
  private message = inject(NzMessageService);
  private userInfoService = inject(UserInfoStoreService);
  private accountService = inject(AccountService);
  private notifications = inject(NotificationsService);
  userInfo = computed(() => {
    return this.userInfoService.$userInfo();
  });

  /** 顶部展示名：优先昵称，否则登录名 */
  displayName = computed(() => {
    const u = this.userInfo();
    const n = (u.nickName || u.userName || '').trim();
    return n || '用户';
  });

  /** 头像地址：无则默认图；相对路径拼 API 根地址 */
  avatarSrc = computed(() => {
    const raw = this.userInfo().avatar?.trim();
    if (!raw) {
      return 'imgs/default_face.png';
    }
    if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) {
      return raw;
    }
    const base = environment.api.baseUrl.replace(/\/$/, '');
    const path = raw.startsWith('/') ? raw : `/${raw}`;
    return `${base}${path}`;
  });

  ngOnInit(): void {
    this.notifications
      .list()
      .pipe(
        take(1),
        map(items => items.filter(i => !i.read).length),
        catchError(() => of(0))
      )
      .subscribe(n => this.unreadBadgeCount.set(n));
  }

  // 锁定屏幕
  lockScreen(): void {
    this.lockWidgetService
      .show({
        nzTitle: '锁定屏幕',
        nzStyle: { top: '25px' },
        nzWidth: '520px',
        nzFooter: null,
        nzMaskClosable: true
      })
      .subscribe();
  }

  // 修改密码
  changePassWorld(): void {
    this.changePasswordModalService.show({ nzTitle: '修改密码' }).subscribe(({ modalValue, status }) => {
      if (status === ModalBtnStatus.Cancel) {
        return;
      }
      const uid = this.userInfo().id;
      this.user = {
        id: uid,
        oldPassword: modalValue.oldPassword,
        newPassword: modalValue.newPassword
      } satisfies UserPsd;
      this.accountService.editAccountPsd(this.user).subscribe(() => {
        this.loginOutService.loginOut().then();
        this.message.success('修改成功，请重新登录');
      });
    });
  }

  showSearchModal(): void {
    const modalOptions: ModalOptions = {
      nzClosable: false,
      nzMaskClosable: true,
      nzStyle: { top: '48px' },
      nzFooter: null,
      nzBodyStyle: { padding: '0' }
    };
    this.searchRouteService.show(modalOptions);
  }

  goLogin(): void {
    this.loginOutService.loginOut().then();
  }

  clean(): void {
    this.windowServe.clearStorage();
    this.windowServe.clearSessionStorage();
    this.loginOutService.loginOut().then();
    this.message.success('清除成功，请重新登录');
  }

  showMessage(): void {
    this.message.info('切换成功');
  }

  goPage(path: string): void {
    this.router.navigateByUrl(`/default/personal/${path}`);
  }
}
