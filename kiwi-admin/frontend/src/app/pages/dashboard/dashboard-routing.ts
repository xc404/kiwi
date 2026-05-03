import { Route } from '@angular/router';

export default [
  { path: '', redirectTo: 'monitor', pathMatch: 'full' },
  {
    title: '分析页',
    path: 'analysis',
    data: { preload: true, key: 'analysis' },
    loadComponent: () => import('./analysis/analysis.component').then(m => m.AnalysisComponent)
  },
  { path: 'monitor', title: '监控页', data: { key: 'monitor' }, loadComponent: () => import('./monitor/monitor.component').then(m => m.MonitorComponent) },
  { path: 'workbench', title: '工作台', data: { key: 'workbench' }, loadComponent: () => import('./workbench/workbench.component').then(m => m.WorkbenchComponent) },
  // 侧栏菜单需在「系统管理-菜单」中配置路由：/dashboard/ai-chat
  {
    path: 'ai-chat',
    title: 'AI 对话',
    data: { key: 'dashboard-ai-chat' },
    loadComponent: () => import('./ai-chat/ai-chat.component').then(m => m.AiChatComponent)
  }
] satisfies Route[];
