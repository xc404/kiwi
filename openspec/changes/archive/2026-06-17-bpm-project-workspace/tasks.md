## 1. 实现

- [x] 1.1 新增 `BpmWorkspaceService`，封装 `localStorage` 键 `kiwi.bpm.lastWorkspaceProjectId`
- [x] 1.2 `BpmProjectProcess`：在 `projectId` 有效时写入上次工作区
- [x] 1.3 `BpmProject`：读取记忆；展示「进入上次工作区」→ `/bpm/process-definition?projectId={id}`（非初稿 `/default/bpm/project/:id`）

## 2. 验证

- [x] 2.1 手动：进入某项目流程页后返回项目管理列表，可出现入口并跳回同一项目

## 3. 项目工作区页 UI

- [x] 3.1 `BpmProjectProcess`：`nz-dropdown` 切换项目；切换时更新 `projectId` queryParam
