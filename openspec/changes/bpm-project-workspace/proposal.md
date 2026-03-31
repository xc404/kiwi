## Why

BPM 中「项目」是流程与设计的容器，但路由在「项目列表」与「某项目下的流程」之间切换时，没有统一的工作区概念；用户每次从菜单进入项目管理都要重新点进项目。需要 **以 project 为工作区**，并在浏览器侧 **记住上次进入的项目**，便于一键回到上次上下文。

## What Changes

- **前端**：引入 **工作区记忆**（`localStorage` 键，仅存当前用户浏览器侧上次选中的 `projectId`）。
- **进入项目流程页**（`/default/bpm/project/:id`）时写入该 id。
- **项目管理列表页**（`/default/bpm/project`）在存在记忆时展示 **进入上次工作区** 的入口（跳转至对应 `:id`）。
- **OpenSpec**：新增能力规格 `bpm-workspace`，描述持久化键与 UI 行为。

## Capabilities

### New Capabilities

- `bpm-workspace`：BPM 以项目为工作区；客户端记住上次 `projectId` 并提供列表页快捷进入。

### Modified Capabilities

- （无）

## Impact

- **代码**：`kiwi-admin/frontend` 新增 `BpmWorkspaceService`（或等价封装）；`bpm-project.ts`、`bpm-project-process.ts` 接入；可选轻量 UI（Alert/按钮）。
- **数据**：仅浏览器本地存储，不涉及后端 schema；清除站点数据会丢失记忆。
