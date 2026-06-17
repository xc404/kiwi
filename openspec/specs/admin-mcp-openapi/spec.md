# admin-mcp-openapi Specification

## Purpose
TBD - created by archiving change swagger-mcp-plug-server2mcp-webmvc. Update Purpose after archive.
## Requirements
### Requirement: MCP 业务工具由 OpenAPI 注解驱动

Kiwi 管理后台 SHALL 通过 `spring-ai-starter-mcp-server-webmvc` 对外提供 MCP 能力。业务类 MCP 工具的主要元数据 SHALL 来源于 `com.kiwi.project` 包下 `@RestController` 方法上的 `@Operation(operationId, summary)`，由 `KiwiOpenApiSyncMcpToolsConfiguration` 扫描并注册为 `McpServerFeatures.SyncToolSpecification`，SHALL NOT 依赖控制器上的 `org.springframework.ai.tool.annotation.Tool`。

#### Scenario: 新 REST 端点默认可被 MCP 消费

- **WHEN** 开发者在已纳入扫描范围的 `@RestController` 上新增带 `@Operation(operationId, summary)` 的 HTTP 端点
- **THEN** 该端点 SHALL 出现在 MCP `tools/list` 中，工具名为 `operationId`，摘要与 OpenAPI 一致，无需新增 `@Tool`

### Requirement: 弃用基于 @Tool 的业务 MCP 注册

为 MCP 而存在于控制器上的 `@Tool` 标注 SHALL 被移除。`KiwiToolCallbackConfiguration` 式「全容器扫描 `@Tool`」SHALL NOT 再作为 MCP 或业务工具来源。

#### Scenario: 控制器无 MCP 专用 @Tool

- **WHEN** 审计原先带 `@Tool` 的管理后台 `@RestController`
- **THEN** 这些类 SHALL 不再包含为 MCP 而写的 `@Tool`，等价能力由 OpenAPI 扫描提供

### Requirement: ChatClient 业务工具与对外 MCP 同源

`kiwiChatClient` 的业务类工具 SHALL 通过本机 `McpSyncClient`（SSE 回环）与 `SyncMcpToolCallbackProvider` 获取，与外部 MCP 客户端可见的业务工具在**名称与参数契约**上一致。SHALL NOT 为业务 API 单独维护与 MCP 列表不同的 `@Tool` 扫描结果。

#### Scenario: 助手与外部 MCP 业务工具名一致

- **WHEN** 列出外部 MCP 客户端可见的业务工具名与助手对话中经 MCP 回环调用的业务工具名
- **THEN** 二者在业务工具集合上 SHALL 一致（均为各 `@Operation.operationId`）

### Requirement: 助手客户端动作进程内 @Tool

`assistant_navigate` 与 `assistant_designer_*` 等**仅登记 `AiAssistantResponse.actions`、无独立业务 REST 语义**的助手动作，SHALL 由 `AssistantNavigationTools` 与 `AssistantDesignerTools`（及经架构评审的同类 Bean）以 `@Tool` 实现，并 SHALL 仅通过 `KiwiAssistantInProcessToolsFactory` 注册到 `kiwiChatClient`，SHALL NOT 出现在对外 MCP `tools/list` 中（避免与 `AssistantClientActionContext` 线程语义冲突及重复注册）。

#### Scenario: MCP 列表不含 assistant_*

- **WHEN** 外部 MCP 客户端调用 `tools/list`
- **THEN** 返回列表 SHALL 仅含 OpenAPI 扫描的业务工具，SHALL NOT 含 `assistant_navigate` 或 `assistant_designer_*`

#### Scenario: 助手仍可使用 assistant_*

- **WHEN** 用户通过内置助手发起需登记前端 actions 的对话
- **THEN** `kiwiChatClient` SHALL 可调用 `assistant_*` 工具，且 `SYSTEM_PROMPT` 中的命名与 `@Tool.name` 一致

### Requirement: OpenAPI 注解完整性

所有拟暴露为 MCP 业务工具的 HTTP API 分组 SHALL 具备类级 `@Tag`。每个操作 SHALL 具备 `@Operation` 且 **MUST** 包含非空 `operationId` 与 `summary`。路径变量、查询参数或请求体字段若不能从类型名推断语义，则 SHALL 通过 `@Parameter`、`@Schema` 或 DTO 字段注解补充说明。

#### Scenario: 缺省文档不足时不注册为 MCP 工具

- **WHEN** 某 `@RestController` 方法缺少 `@Operation` 或缺少 `operationId`/`summary`
- **THEN** `KiwiOpenApiSyncMcpToolsConfiguration` SHALL 跳过该方法，不将其注册为 MCP 工具

### Requirement: 认证与暴露范围

MCP 调用 SHALL 使用与现有 REST API 相同的安全机制（`Authorization: Bearer` 与 Sa-Token）。扫描范围 SHALL 限定为 `com.kiwi.project` 包前缀，并 SHALL 排除 `AiChatCtl` 与 `.integration.` 包；SHALL NOT 默认暴露 Camunda `/engine-rest` 等非本应用业务控制器。

#### Scenario: 未认证调用被拒绝

- **WHEN** MCP 客户端在未携带有效 Bearer Token 的情况下调用需登录的管理接口
- **THEN** 服务器 SHALL 返回与 REST 一致的错误语义（如 401）

### Requirement: 可运维配置

项目 SHALL 提供 `spring.ai.mcp.server.enabled`（或等价环境变量 `SPRING_AI_MCP_SERVER_ENABLED`）及 `kiwi.ai.enabled`（`KIWI_AI_ENABLED`）开关。关闭 MCP 时应用 SHALL 正常启动，HTTP API SHALL 不受影响。

#### Scenario: 运维可关闭 MCP

- **WHEN** 运维将 `SPRING_AI_MCP_SERVER_ENABLED` 设为 `false`
- **THEN** 应用 SHALL 正常启动，且 SHALL 不对外提供 MCP SSE 监听

