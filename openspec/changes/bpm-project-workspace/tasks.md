## 1. 实现

- [x] 1.1 新增 `BpmWorkspaceService`（或等价），封装 `localStorage` 键 `kiwi.bpm.lastWorkspaceProjectId` 的读写
- [x] 1.2 `BpmProjectProcess`：在路由 `projectId` 有效时写入上次工作区
- [x] 1.3 `BpmProject`：读取记忆；存在时展示「进入上次工作区」并跳转 `/default/bpm/project/:id`

## 2. 验证

- [ ] 2.1 手动：进入某项目流程页后返回项目管理列表，应出现入口并可跳回同一项目（需本地运行前端）

## 3. 项目工作区页 UI

- [x] 3.1 `BpmProjectProcess`：以 `nz-dropdown` + `nz-menu` 切换项目（下拉内支持搜索过滤），并优化工具栏信息布局；切换项目时跳转 `/default/bpm/project/:id`
