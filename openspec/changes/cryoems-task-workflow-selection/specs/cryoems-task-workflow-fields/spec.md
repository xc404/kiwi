## ADDED Requirements

### Requirement: Task 工作流字段
`Task` 模型 SHALL 暴露两个字符串字段用于绑定该任务使用的 Kiwi `BpmProcess` id：`movieProcessDefinitionId`（Movie 流水线，已存在但 MUST 从 `@Hidden` 中移除并允许 API 写入）与新增的 `mdocProcessDefinitionId`（mdoc 流水线，仅在 `Task.is_tomo=true` 时有意义）。两个字段 MUST 都是可空字符串：未填表示走默认值层。

#### Scenario: 创建任务时写入 movie 字段
- **WHEN** 客户端 `POST /api/task` 提交的 JSON 顶层包含 `movieProcessDefinitionId="MP1"`
- **THEN** 持久化的 `Task` 文档中该字段值为 `"MP1"`，且 `GET /api/task/{id}` 响应中可回读该字段

#### Scenario: 创建 tomo 任务时写入 mdoc 字段
- **WHEN** 数据集 `is_tomo=true`，客户端提交 JSON 同时包含 `movieProcessDefinitionId="MP1"` 与 `mdocProcessDefinitionId="MD1"`
- **THEN** 持久化的 `Task` 文档同时保留两个字段，且 `task.is_tomo=true`

#### Scenario: 未填字段保持 null
- **WHEN** 客户端提交 JSON 不包含 `movieProcessDefinitionId` 与 `mdocProcessDefinitionId`
- **THEN** 持久化的 `Task` 文档中两个字段为 `null`，未来由 Service 解析层使用默认值

#### Scenario: 字段对 Swagger 可见
- **WHEN** 查阅 cryo-web 的 OpenAPI / Swagger 文档
- **THEN** `Task.movieProcessDefinitionId` 与 `Task.mdocProcessDefinitionId` 出现在 `Task` schema 的属性列表中（不再被 `@Hidden` 标注）

### Requirement: Movie 流程三段回退解析
`MovieKiwiWorkflowService.resolveMovieBpmProcessId(task)` SHALL 按以下严格优先级顺序解析待启动的 BpmProcess id，并在解析时输出 DEBUG 日志记录命中的层级：① 当 `task.movieProcessDefinitionId` 非空时使用之；② 当 `app.kiwi.workflow.process-types.movie` 已配置时，按 `task.is_tomo` 选 `defaultIdTomo` 或 `defaultIdNonTomo`，非空即使用；③ 兜底使用 `app.kiwi.workflow.movie-process-definition-id`（旧全局回退）。三层全部为空时 SHALL 返回 `null` 或抛出与现有 `MovieEngine` 检查兼容的"未配置"异常。

#### Scenario: Task 字段优先
- **WHEN** `task.movieProcessDefinitionId="MP1"` 且 `process-types.movie.defaultIdTomo="MP2"`
- **THEN** 解析结果为 `"MP1"`

#### Scenario: 落到 type 默认（tomo）
- **WHEN** `task.movieProcessDefinitionId=null`、`task.is_tomo=true`、`process-types.movie.defaultIdTomo="MP2"`
- **THEN** 解析结果为 `"MP2"`

#### Scenario: 落到 type 默认（non-tomo）
- **WHEN** `task.movieProcessDefinitionId=null`、`task.is_tomo=false`、`process-types.movie.defaultIdNonTomo="MP3"`
- **THEN** 解析结果为 `"MP3"`

#### Scenario: 落到旧全局回退
- **WHEN** `task.movieProcessDefinitionId=null` 且 `process-types.movie` 未配置或对应 default 为空
- **AND** `app.kiwi.workflow.movie-process-definition-id="MP_LEGACY"`
- **THEN** 解析结果为 `"MP_LEGACY"`，确保未升级配置的部署零变更继续工作

#### Scenario: 全部为空
- **WHEN** 三层来源均未提供 BpmProcess id
- **THEN** 解析返回空，`isMoviePipelineReady(task)` 返回 `false`，`MovieEngine` 启动时按现有"未配置"路径拒绝运行该 task

### Requirement: Mdoc 流程解析与启动占位
cryo-em-server-backend SHALL 提供 `MdocKiwiWorkflowService.resolveMdocBpmProcessId(task)`，其行为与 Movie 解析一致但仅基于以下两层：① `task.mdocProcessDefinitionId`；② `process-types.mdoc.defaultIdTomo`/`defaultIdNonTomo`（按 `task.is_tomo`）。Mdoc 路径 MUST NOT 回退到 `movie-process-definition-id`。`MdocKiwiWorkflowService` 同时 SHALL 提供与 `MovieKiwiWorkflowService.ensureStarted` 形态一致的对外方法签名（即使本变更不接入实际调度），便于后续 mdoc 引擎接入。

#### Scenario: Mdoc Task 字段优先
- **WHEN** `task.mdocProcessDefinitionId="MD1"` 且 `process-types.mdoc.defaultIdTomo="MD_DEF"`
- **THEN** 解析结果为 `"MD1"`

#### Scenario: 落到 mdoc tomo 默认
- **WHEN** `task.mdocProcessDefinitionId=null`、`task.is_tomo=true`、`process-types.mdoc.defaultIdTomo="MD_DEF"`
- **THEN** 解析结果为 `"MD_DEF"`

#### Scenario: 不回退到 movie 全局
- **WHEN** `task.mdocProcessDefinitionId=null`、`process-types.mdoc` 未配置
- **AND** `app.kiwi.workflow.movie-process-definition-id="MP_LEGACY"`
- **THEN** 解析返回空，且不会错误地将 `MP_LEGACY` 当作 mdoc 流程使用

#### Scenario: Mdoc 服务存在但调度链路 out-of-scope
- **WHEN** 任何客户端在 `Task.is_tomo=true` 的任务上启动 cryo-web 处理流程（`POST /api/task/{id}/start`）
- **THEN** `MdocKiwiWorkflowService.resolveMdocBpmProcessId(task)` 可被未来引擎调用并返回正确解析值，但本变更不要求 cryo-web 在 task start 时立即触发 mdoc 流程实例
