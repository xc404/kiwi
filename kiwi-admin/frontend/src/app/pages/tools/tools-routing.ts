import { Route } from '@angular/router';

export default [
  { path: '', redirectTo: 'codegen', pathMatch: 'full' },
  { path: 'codegen', title: '代码生成', data: { key: 'codegen' }, loadComponent: () => import('./codegen/codegen.component').then(m => m.CodegenComponent) },
  { path: 'connection', title: '数据库连接', data: { key: 'connection' }, loadComponent: () => import('./jdbc/connection.component').then(m => m.ConnectionComponent) },
] satisfies Route[];
