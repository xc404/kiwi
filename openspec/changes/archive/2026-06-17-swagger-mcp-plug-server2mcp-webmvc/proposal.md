## Why

当前 Kiwi 管理后台通过 `spring-ai-starter-mcp-server-webmvc` 与大量控制器上的 `@Tool` 将同一业务能力暴露为 MCP 工具，与既有 REST/OpenAPI 重复维护，易出现 HTTP 与 MCP 语义不一致。采用 `com.ai.plug:server2mcp-starter-webmvc` 以 OpenAPI 3（Springdoc）为主源生成 MCP，可减少手写 `@Tool`，并与 Swagger 文档单一事实源对齐。对缺少 Springdoc 元数据的接口，需约定缺省/补充注解策略，保证模型可用的摘要与参数说明。

## What Changes

- 引入 **`com.ai.plug:server2mcp-starter-webmvc`**（或项目选定的兼容坐标），按该 starter 的方式注册/暴露 MCP，替代「依赖 `@Tool` + `MethodToolCallbackProvider` 扫描」作为 **对外 MCP 服务** 的主要机制。
- **移除或显著削减** 各 `@RestController`（及少量 `@Service`）上为 MCP 而写的 `@Tool` 及与之绑定的 MCP 专用描述；保留的业务代码以标准 Spring Web + OpenAPI 3 注解为主。
- **以 Springdoc/OpenAPI 3 为 MCP 工具元数据主来源**；对无 `@Operation`/`@Parameter` 等充分描述的端点，补充 **缺省或统一的 Swagger 3 注解**（类级 `@Tag`、方法级 `@Operation`、参数 `@Parameter` 等），必要时辅以 starter 支持的扫描/过滤注解（如文档中的 `@ToolScan`/`@ResourceScan` 等，以实际 starter 文档为准）。
- **`spring-ai-starter-mcp-server-webmvc` 与 Spring AI MCP Server 相关配置** 的取舍在实现阶段明确：**BREAKING**：外部 MCP 客户端连接方式、路径、能力列表可能与现网基于 Spring AI MCP 的实现不同；需在 `application.yml` 与部署说明中体现。
- **`ChatClient` 与 `ToolCallbackProvider`**：**已定案** 助手侧改为与 **server2mcp 暴露能力一致的另一套集成**——通过 starter 提供的 Spring AI 桥接、或从 **同一 OpenAPI** 生成的 `ToolCallback` 注册到 `ChatClient`，**不得**再依赖全项目扫描 `@Tool` 的 `MethodToolCallbackProvider` 作为助手工具来源；`SYSTEM_PROMPT` 中的工具名/用法须与 MCP 侧一致。

## Capabilities

### New Capabilities

- `admin-mcp-openapi`：Kiwi 管理后台 MCP 由 REST/OpenAPI 3 驱动；认证与暴露范围；弃用基于 `@Tool` 的 MCP 工具注册方式；缺省 Swagger 注解约定与文档质量门禁。

### Modified Capabilities

- （无）当前 `openspec/specs/` 下无与 Kiwi 管理后台 MCP 相关的既有能力规格，不要求 delta。

## Impact

- **依赖**：`kiwi-admin/backend/pom.xml` 增加 server2mcp starter；可能移除或降级 `spring-ai-starter-mcp-server-webmvc`，并处理与 Spring AI Alibaba / Spring Boot 的版本对齐。
- **代码**：`com.kiwi.project.ai.mcp` 包内 `KiwiToolCallbackConfiguration`、`KiwiAdminAiMcpConfiguration` 及所有带 `@Tool` 的控制器/服务；`application.yml` 中 `spring.ai.mcp.server` 等配置。
- **运维/集成**：依赖 MCP 的客户端需按新传输与工具清单适配；若 starter 非 Maven Central 发布，需补充私服或构建说明。
