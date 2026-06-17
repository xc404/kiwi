# 归档说明（spec 路由过时，不同步入 main）

**日期：** 2026-06-17

## 实现（以代码为准）

- `BpmWorkspaceService`：`kiwi.bpm.lastWorkspaceProjectId`
- 工作区页：`/bpm/process-definition?projectId=`（`bpm-project-process.ts`）
- 列表快捷入口：`BpmProject.goLastWorkspace()` → 同上 queryParam
- 进入工作区或切换项目时 `setLastProjectId`

## 为何 delta spec 过时

初稿与 delta spec 使用已废弃的 `/default/bpm/project/:id` 路径（见 `admin-frontend-layout-routing-cleanup`）。当前无 `:id` 段路由，工作区以 **queryParam `projectId`** 标识项目。

归档使用 `--skip-specs`；若需 main spec，应新开 change 按现行路由重写 `bpm-workspace`。
