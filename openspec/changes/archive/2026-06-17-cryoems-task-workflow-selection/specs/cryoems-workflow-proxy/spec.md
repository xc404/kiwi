## ADDED Requirements

### Requirement: 工作流类型配置
cryo-em-server-backend SHALL 在 `app.kiwi.workflow.process-types` 下提供按"工作流类型"组织的配置组，每个 type（如 `movie`、`mdoc`）MUST 至少支持以下三个可选属性：`projectId`（kiwi-admin 中承载该 type 入口流程的 BpmProject 主键）、`defaultIdNonTomo`（数据集 `is_tomo=false` 时使用的默认 BpmProcess id）、`defaultIdTomo`（数据集 `is_tomo=true` 时使用的默认 BpmProcess id）。该配置组 MUST 默认空 Map，缺省时不影响系统运行。

#### Scenario: 加载配置
- **WHEN** `application.yml` 中存在 `app.kiwi.workflow.process-types.movie.project-id=P1`、`default-id-non-tomo=PD1`、`default-id-tomo=PD2`
- **THEN** Spring 启动后 `KiwiWorkflowProperties.processTypes` 中 key="movie" 的条目对应 `projectId=P1`、`defaultIdNonTomo=PD1`、`defaultIdTomo=PD2`

#### Scenario: 缺省配置
- **WHEN** `application.yml` 中没有 `app.kiwi.workflow.process-types` 段
- **THEN** `KiwiWorkflowProperties.processTypes` 为空 Map，启动不报错

#### Scenario: 部分类型缺省
- **WHEN** 配置中只声明了 `process-types.movie` 而没有 `process-types.mdoc`
- **THEN** `processTypes` 仅包含 `movie` 一项，调用方查询 `mdoc` 时返回 `null`/`Optional.empty()`

### Requirement: 工作流代理列表端点
cryo-em-server-backend SHALL 暴露 `GET /api/bpm/processes?type=<type>` 端点，使前端能够按 type 拉取可选的入口流程清单。该端点 MUST 在内部完成以下步骤：① 校验 `type` 已在 `process-types` 中配置，否则返回 4xx；② 通过 `KiwiClient` 调 kiwi-admin 的 `GET /bpm/process/entries?projectId=<配置中的 projectId>&deployed=true`；③ 将 Kiwi 返回的流程清单映射为简化 DTO（仅含 `id`、`name`、`deployedVersion`、`deployedAt`）回传前端。该端点 MUST 受现有登录鉴权保护（`@SaCheckLogin` 等价机制），且对所有已登录普通用户可访问。

#### Scenario: 已配置 type 拉取列表
- **WHEN** 已登录用户调用 `GET /api/bpm/processes?type=movie`
- **AND** 配置中存在 `process-types.movie.project-id=P1`
- **AND** kiwi-admin 在 P1 下存在两条满足"已部署入口"条件的流程 A、B
- **THEN** cryo-web 端调用 `GET /bpm/process/entries?projectId=P1` 并将结果以 `[{id, name, deployedVersion, deployedAt}, ...]` 形式返回前端

#### Scenario: 未配置 type
- **WHEN** 已登录用户调用 `GET /api/bpm/processes?type=foo`
- **AND** `process-types.foo` 未配置
- **THEN** 端点返回 HTTP 400（或 422）并附带可读的错误信息说明"未知的工作流类型 foo"

#### Scenario: Kiwi 返回空
- **WHEN** type 已配置但 kiwi-admin 在该 projectId 下无任何已部署入口流程
- **THEN** 端点返回空数组，且不抛出 5xx

#### Scenario: Kiwi 不可达
- **WHEN** Kiwi 客户端未配置（PAT/Sa-Token 不可用）或 Kiwi 服务返回 5xx
- **THEN** 端点返回 HTTP 5xx 与可读错误信息，且不缓存错误响应；前端可重试

#### Scenario: DTO 不暴露内部信息
- **WHEN** 端点把 Kiwi 返回的 `BpmProcess` 序列化给前端
- **THEN** 响应 MUST NOT 包含 `bpmnXml`、`projectId`、`createdBy`、`createdTime` 等内部字段
