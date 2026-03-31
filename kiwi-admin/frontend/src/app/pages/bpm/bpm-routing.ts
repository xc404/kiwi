import { Route } from '@angular/router';

export default [
  { path: '', redirectTo: 'project', pathMatch: 'full' },
  { path: 'com', title: '组件管理', data: { key: 'bpm-component' }, loadComponent: () => import('./component/bpm-component').then(m => m.BpmComponent) },
  { path: 'project', title: '项目管理', data: { key: 'bpm-project' }, loadComponent: () => import('./project/bpm-project').then(m => m.BpmProject) },
  { path: 'process', title: '项目流程', data: { key: 'bpm-project-process' }, loadComponent: () => import('./project/bpm-project-process').then(m => m.BpmProjectProcess) },
  {
    path: 'process-instances',
    title: '运行实例',
    data: { key: 'bpm-process-instances' },
    loadComponent: () => import('./process-instances/bpm-process-instances').then(m => m.BpmProcessInstances),
  },
] satisfies Route[];