import { Route } from '@angular/router';

import { MainComponent } from './main.component';

export default [
  {
    path: '',
    component: MainComponent,
    data: { shouldDetach: 'no', preload: true },
    // canActivateChild: [JudgeLoginGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        data: { preload: true },
        loadChildren: () => import('../pages/dashboard/dashboard-routing')
      },
      {
        path: 'personal',
        loadChildren: () => import('../pages/personal/personal-routing')
      },
      {
        path: 'system',
        loadChildren: () => import('../pages/system/system-routing')
      },
      {
        path: 'tools',
        loadChildren: () => import('../pages/tools/tools-routing')
      },
      {
        path: 'bpm',
        loadChildren: () => import('../pages/bpm/bpm-routing')
      },
      // 此路由用于tab刷新时占位组件
      {
        path: 'refresh-empty',
        title: 'refresh-empty',
        data: { key: 'refresh-empty', shouldDetach: 'no' },
        loadComponent: () => import('./refresh-empty/refresh-empty.component').then(m => m.RefreshEmptyComponent)
      }
    ]
  }
] satisfies Route[];
