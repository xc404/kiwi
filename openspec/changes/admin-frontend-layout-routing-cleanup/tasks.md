## 1. 布局目录整理

- [x] 1.1 将 `kiwi-admin/frontend/src/app/layout/default/` 下文件迁移至 `layout/`（或设计稿中的目标结构），删除空的 `layout/default/` 目录
- [x] 1.2 将 `default-routing.ts` 重命名为 `layout-routing.ts`（或等价名称），并修正其中所有相对 `import`（如 `refresh-empty`、各 `pages` 懒加载路径）
- [x] 1.3 更新壳组件及其子组件内所有 `@app/layout/default/...` 与相对路径引用，确保 `@app/layout/...` 或相对路径与物理位置一致

## 2. 顶层路由与懒加载

- [x] 2.1 修改 `app.routes.ts`：移除 `path: 'default'`，使带守卫的主壳路由在 `path: ''`（或约定路径）下 `loadChildren` 加载新路由模块；根 `redirectTo` 指向业务默认页（如 `dashboard`），不再指向 `/default`
- [x] 2.2 确认 `login`、`bpm/design/:id`、`bpm/process-instance/:id` 等独立路由与新区块无冲突，`JudgeLoginGuard` 仍作用于主壳子树

## 3. 全应用路径与导航替换

- [x] 3.1 替换 `tab.service.ts`、`nav-bar.component.ts` 等与 `/default/refresh-empty` 或 URL 前缀相关的逻辑
- [x] 3.2 替换 `login-form.component.ts`、`layout-head-right-menu.component.ts`、`home-notice.component.html`、`bpm-project.ts`、`bpm-project-process.ts` 等中的 `navigate` / `routerLink` / 字符串 URL
- [x] 3.3 全局检索 `default/`、`/default`（排除资源文件名如 `default_face.png`），清理残留引用并更新注释（如 `dashboard-routing.ts` 内菜单配置说明）

## 4. 配置与数据

- [x] 4.1 检索后端或种子数据中菜单 `path` 是否含 `/default` 前缀；若有，制定并执行迁移（SQL 或管理端批量修改），并记录在变更说明中  
  （仓库内无 SQL 种子命中；若库中 `sys_menu.path` 仍含 `/default/`，可在维护窗口执行：`UPDATE sys_menu SET path = REPLACE(path, '/default/', '/') WHERE path LIKE '/default/%';`，并在管理端核对外链菜单。）

## 5. 验证

- [x] 5.1 执行 `ng build`（或项目既定构建命令）通过
- [ ] 5.2 手工回归：登录跳转、侧栏多模块跳转、Tab 关闭/刷新、BPM 相关跳转、个人中心通知链接
