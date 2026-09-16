## Why

工作流侧栏同时有「项目管理」和「流程管理」：前者只是项目 CRUD，后者才是某项目下的流程列表。用户要管流程必须先点项目再点「流程管理」，两套入口重复且易迷路。需要把设计入口收成 **项目管理**（`/bpm/project`），流程列表与项目生命周期在同一页完成。

## What Changes

- 侧栏只保留「项目管理」；下线「流程管理」菜单（`bpm_process_definition`）。
- `/bpm/project` 加载「当前项目 + 其下流程列表」（现 `BpmProjectProcess`）；`/bpm` 与 `/bpm/process-definition` 落到该页（后者 **BREAKING** 对直接依赖旧路径的书签：改为 redirect 并保留 `projectId`）。
- 项目管理页补齐新建/重命名/环境变量/从模板新建；删除纯项目列表页。
- 删除项目：有流程则拒绝；无流程则级联删除该项目环境变量。
- 客户端仍记住上次 `projectId`；进入项目管理时自动选中，不再需要列表页「回到上次工作区」横幅。

## Capabilities

### New Capabilities

- `bpm-workspace`：项目管理 `/bpm/project` 为唯一设计入口（当前项目 + 流程列表）；客户端记住上次 `projectId` 并在进入该页时自动选中；删除项目时有流程则拒绝、无流程则级联清环境变量。（`openspec/specs/` 尚无此能力；归档 change `bpm-project-workspace` 的列表页横幅要求在此作废。）

### Modified Capabilities

- （无）

## Impact

- **前端**：`bpm-routing.ts`、`R__SysMenu.json`、`bpm-project-process.ts`、删除 `bpm-project.ts`；模板/远程市场安装后跳转 `/bpm/project?projectId=`。
- **后端**：`BpmProjectCtl` 删除语义；`BpmProject` / `projectId` / env / 模板 API 契约不变。
- **文档**：`docs/bpm-component.zh-CN.md` 路径改为 工作流 → 项目管理。
