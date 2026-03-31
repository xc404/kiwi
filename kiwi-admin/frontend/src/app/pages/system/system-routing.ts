import { Route } from '@angular/router';

export default [
  { path: '', redirectTo: 'dept', pathMatch: 'full' },
  { path: 'menu', title: '菜单管理', data: { key: 'menu' }, loadComponent: () => import('./menu/menu.component').then(m => m.MenuComponent) },
  { path: 'user', title: '用户管理', data: { key: 'user' }, loadComponent: () => import('./user/user.component').then(m => m.UserComponent) },
  
  { path: 'dept', title: '部门管理', data: { key: 'dept' }, loadComponent: () => import('./dept/dept.component').then(m => m.DeptComponent) },
  { path: 'dict', title: '字典管理', data: { key: 'dict' }, loadComponent: () => import('./dict/dict.component').then(m => m.DictComponent) },
  { path: 'role',  title: '角色管理', data: { key: 'role' }, loadComponent: () => import('./role/role.component').then(m => m.RoleComponent) }
] satisfies Route[];
