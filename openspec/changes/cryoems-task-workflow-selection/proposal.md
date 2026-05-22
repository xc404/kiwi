## Why

cryoEMS 任务（Task）的处理流水线已迁移到 Kiwi-admin Camunda 编排（Movie 流程），但创建任务时**用户无法选择**要使用的具体流程：`Task.movieProcessDefinitionId` 字段虽已存在却处于 `@Hidden`，实际只能依赖 `app.kiwi.workflow.movie-process-definition-id` 全局回退配置，导致一台 cryo-web 同一时刻只能跑一个 Movie 流程模板，无法按数据集/采集模式区分。同时即将在 cryo-web 引入 mdoc（tomo 元数据）流水线，也需要相同的流程选择能力。

## What Changes

- 在 cryo-em-server-frontend 创建任务页（`src/app/create/page.tsx` 经由 `GlobalSettings`）的 Project Information 区，新增 **Movie Workflow** 简单 Select；当所选数据集 `is_tomo=true` 时，**额外**显示 **Mdoc Workflow** Select。两者均在创建模式必填，编辑模式禁用展示。
- cryo-em-server-backend 新增受控代理端点 `GET /api/bpm/processes?type={movie|mdoc}`：根据 `type` 在配置中查到对应 Kiwi `projectId` 与默认值，再向 Kiwi-admin 拉取「已部署 + 入口」流程清单并以简化 DTO 返回前端。
- cryo-em-server-backend 在 `KiwiWorkflowProperties` 下新增 `process-types.{movie,mdoc}` 配置组，每个 type 含 `project-id`、`default-id-non-tomo`、`default-id-tomo` 三项；解析任务流程时按 `Task → type+is_tomo 默认 → 旧 movie-process-definition-id` 三段回退。
- cryo-em-server-backend 新增 `Task.mdocProcessDefinitionId` 字段；将 `Task.movieProcessDefinitionId` 与新字段从 `@Hidden` 移除，使前端可写入并在 Swagger 暴露。新增 `MdocKiwiWorkflowService`（与 `MovieKiwiWorkflowService` 同形）用于 mdoc 流水线启动。
- kiwi-admin 在 `BpmProcess` 模型新增 `entry: boolean` 字段（默认 `false`），并新增专用查询端点 `GET /bpm/process/entries?projectId=...&deployed=true`：仅返回 `entry=true` 且 `deployedVersion>0`、`deployedAt!=null` 的流程；BPM 编辑器流程属性面板增加「入口流程」复选框允许用户标记。
- **BREAKING**：`Task.movieProcessDefinitionId` 在 API 中由 `@Hidden` 改为可见；`MovieKiwiWorkflowService.resolveMovieBpmProcessId` 的回退顺序中插入了基于 type 与 `is_tomo` 的默认值层（位于 Task 字段与全局回退之间）。

## Capabilities

### New Capabilities
- `kiwi-bpm-process-entry`: kiwi-admin 端 `BpmProcess` 的「入口流程」标记与按入口/已部署的查询能力。
- `cryoems-workflow-proxy`: cryo-em-server-backend 端面向前端的「按 type 拉取可用流程」代理端点与 type→projectId 配置。
- `cryoems-task-workflow-fields`: cryo-em-server-backend 端 `Task` 上的 `movie/mdoc` 流程字段、保存与三段回退解析能力，以及 mdoc 流水线 Kiwi 集成。
- `cryoems-frontend-workflow-selector`: cryo-em-server-frontend 创建任务页的 Movie/Mdoc Workflow 输入框、按 `is_tomo` 的可见性与默认值联动。

### Modified Capabilities
<!-- 仓库目前仅 openspec/specs/slurm-workdir-cleanup 一个已归档 spec，与本次改动无关，无 modified capabilities -->

## Impact

- **kiwi-admin**:
  - `kiwi-admin/backend`: `BpmProcess.entry` 字段新增（数据库无 schema，Mongo 自动兼容老文档默认 `false`）；`BpmProcessDefinitionCtl` 新增 `GET /bpm/process/entries` 查询端点；保存接口允许写入 `entry`。
  - `kiwi-admin/frontend`: BPM 设计器流程属性面板新增「入口流程」复选框（`property-group-readonly` / 流程属性 provider）。
- **cryo-em-server-backend (cryo-web-server)**:
  - 新增 `BpmProcessProxyCtl`（路径 `/api/bpm/processes`），复用现有 `KiwiClient`/`KiwiWorkflowProperties`。
  - `KiwiWorkflowProperties` 新增 `processTypes: Map<String, ProcessTypeConfig>`；`MovieKiwiWorkflowService.resolveMovieBpmProcessId` 与新增 `MdocKiwiWorkflowService.resolveMdocBpmProcessId` 引入三段回退。
  - `Task` 模型新增 `mdocProcessDefinitionId`，去掉两个流程字段的 `@Hidden`。
  - `application.yml` / `application-local.yml` 新增 `app.kiwi.workflow.process-types` 示例段（默认空 map，向后兼容）。
- **cryo-em-server-frontend**:
  - `src/app/create/page.tsx`、`src/components/task/GlobalSettings.tsx` 新增 Workflow / Mdoc Workflow Select 与按 `is_tomo` 的展示分支。
  - `src/services/tasks.ts`（或新建 `src/services/workflows.ts`）新增 `getWorkflowProcesses(type)` 调用 `/api/bpm/processes?type=...`。
  - `src/components/task/TaskTools.tsx` 中 `taskSettings2Params` 把 `movieProcessDefinitionId` / `mdocProcessDefinitionId` 落到顶层请求体。
- **回退兼容**：未配置 `process-types` 时，旧的 `app.kiwi.workflow.movie-process-definition-id` 仍生效；未升级前端的环境继续使用全局回退；老 Task 文档无 `mdocProcessDefinitionId` 时按 type 默认或全局回退处理。
- **不影响**：Camunda 引擎部署、Kiwi PAT/Sa-Token 兑换、数据集与采集参数面板、`/api/task/{id}/start` 协议。
