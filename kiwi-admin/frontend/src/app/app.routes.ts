import { Route } from '@angular/router';
import { JudgeLoginGuard } from './core/services/common/guard/judgeLogin.guard';

export const appRoutes = [
  { path: '', redirectTo: '/default', pathMatch: 'full' },
  { path: 'login', data: { preload: true }, loadChildren: () => import('./pages/login/login-routing') },
  { path: 'default', data: { preload: true }, canActivate: [JudgeLoginGuard], loadChildren: () => import('./layout/default/default-routing') },
  {
    path: 'bpm/design/:id', data: { preload: true }, canActivate: [JudgeLoginGuard],
    loadComponent: () => import('./pages/bpm/design/editor/bpm-editor').then(m => m.BpmEditor)
  },
  {
    path: 'bpm/view/:id', data: { preload: true }, canActivate: [JudgeLoginGuard],
    loadComponent: () => import('./pages/bpm/design/viewer/bpm-viewer').then(m => m.BpmViewer)
  },
  {
    path: 'bpm/process-instance/:processInstanceId', data: { preload: true }, canActivate: [JudgeLoginGuard],
    loadComponent: () =>
      import('./pages/bpm/design/viewer/bpm-process-instance-viewer').then(m => m.BpmProcessInstanceViewer),
  },
] satisfies Route[];
