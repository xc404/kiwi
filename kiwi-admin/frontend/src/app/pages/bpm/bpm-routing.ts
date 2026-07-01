import { Route } from '@angular/router';

export default [
  { path: '', redirectTo: 'project', pathMatch: 'full' },
  { path: 'component', title: '组件管理', data: { key: 'bpm-component' }, loadComponent: () => import('./flow-elements/bpm-component').then(m => m.BpmComponent) },
  {
    path: 'plugins',
    title: '组件插件',
    data: { key: 'bpm-component-plugin' },
    loadComponent: () => import('./flow-elements/bpm-component-plugin').then(m => m.BpmComponentPlugin)
  },
  { path: 'project', title: '项目管理', data: { key: 'bpm-project' }, loadComponent: () => import('./project/bpm-project').then(m => m.BpmProject) },
  { path: 'market', title: '模板市场', data: { key: 'bpm-market' }, loadComponent: () => import('./market/bpm-market').then(m => m.BpmMarket) },
  { path: 'market/:packId', title: '模板详情', data: { key: 'bpm-market-detail' }, loadComponent: () => import('./market/bpm-market-detail').then(m => m.BpmMarketDetail) },
  { path: 'process-definition', title: '项目流程', data: { key: 'bpm-project-process' }, loadComponent: () => import('./project/bpm-project-process').then(m => m.BpmProjectProcess) },
  {
    path: 'process-instances',
    title: '运行实例',
    data: { key: 'bpm-process-instances' },
    loadComponent: () => import('./runtime/bpm-process-instances').then(m => m.BpmProcessInstances)
  }
] satisfies Route[];
