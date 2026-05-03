import { Route } from '@angular/router';

export default [
  { path: '', redirectTo: 'personal-setting', pathMatch: 'full' },
  {
    path: 'personal-setting',
    title: '个人设置',
    data: { key: 'personal-setting' },
    loadComponent: () => import('./personal-setting/personal-setting.component').then(m => m.PersonalSettingComponent)
  },
  {
    path: 'notifications',
    title: '消息通知',
    data: { key: 'notifications' },
    loadComponent: () => import('./notifications/notifications.component').then(m => m.NotificationsComponent)
  }
] satisfies Route[];
