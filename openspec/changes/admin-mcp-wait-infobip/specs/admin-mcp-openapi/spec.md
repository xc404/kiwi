## ADDED Requirements

### Requirement: 过渡期 MCP 实现路径

在 infobip-openapi-mcp 未通过 `admin-mcp-infobip-gate` 采纳门槛期间，Kiwi SHALL 维持 `openspec/specs/admin-mcp-openapi/spec.md` 已规定的 MCP 架构（Spring AI MCP Server、`KiwiOpenApiSyncMcpToolsConfiguration`、`kiwiChatClient` 本机 SSE 回环、`assistant_*` 进程内隔离），SHALL NOT 为等待 infobip 而拆除或降级该架构。

#### Scenario: 等待期间架构不变

- **WHEN** `admin-mcp-wait-infobip` change 处于活跃或已归档且 infobip 门槛未满足
- **THEN** 对外 MCP SHALL 仍由 `spring-ai-starter-mcp-server-webmvc` 提供
- **THEN** 业务工具 SHALL 仍由 `@Operation(operationId, summary)` 扫描注册

### Requirement: 过渡期 schema 增强优先自研

在 infobip 未采纳前，若需提升 MCP 工具 `inputSchema` 质量，项目 SHALL 优先通过增强 `KiwiOpenApiSyncMcpToolsConfiguration`（或同等自研组件）从 **Springdoc 产出的 OpenAPI 文档**生成 schema，并 SHALL 保留 `MethodToolCallback` 进程内直调 `RestController` 方法；SHALL NOT 以「等待 infobip」为由阻塞此类自研增强的独立 change。

#### Scenario: 自研 schema 与 infobip 等待解耦

- **WHEN** 团队计划改进 MCP 参数 schema
- **THEN** 可另开 OpenSpec change 实现 Springdoc 驱动 schema
- **THEN** 该工作 SHALL NOT 要求先引入 infobip 依赖

### Requirement: 第三方 OpenAPI MCP 框架采纳前置条件

任何替代 `KiwiOpenApiSyncMcpToolsConfiguration` 的第三方 OpenAPI→MCP 框架（含 infobip-openapi-mcp、api2mcp4j、Sidecar 等）在合并生产代码前，SHALL 满足 `admin-mcp-infobip-gate`（对 infobip）或等价的版本/兼容性门槛，且 SHALL 通过设计评审证明符合本规格中「ChatClient 与对外 MCP 同源」「assistant_* 不进 tools/list」「Sa-Token 认证」「com.kiwi.project 扫描范围」等既有要求。

#### Scenario: 未评审的第三方框架不得上线

- **WHEN** 某第三方 OpenAPI→MCP 方案未完成门槛与 PoC 验收
- **THEN** 该方案 SHALL NOT 作为默认 MCP 业务工具注册路径合并到 main 分支
