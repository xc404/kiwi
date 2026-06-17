## 1. 依赖与概念验证

- [x] 1.1 评估 `com.ai.plug:server2mcp-starter-webmvc` 可行性  

  **说明**：上游依赖 `io.modelcontextprotocol.sdk:mcp:0.14.0-SNAPSHOT` 在 Central/Spring 仓库不可解析，**未加入** POM；改由 `KiwiOpenApiSyncMcpToolsConfiguration` 自研 OpenAPI→`MethodToolCallback` 扫描。

- [x] 1.2 OpenAPI 与 MCP 端点连通性  

  **说明**：`spring-ai-starter-mcp-server-webmvc` + `kiwiOpenApiMcpSyncTools` Bean；`mvn -pl kiwi-admin/backend -am compile` 通过。运行时 smoke：`/v3/api-docs`、MCP `tools/list`（运维验收项）。

## 2. MCP 运行时（server2mcp 未采纳）

- [x] 2.1 **保留** `spring-ai-starter-mcp-server-webmvc`（不替换为 server2mcp）  

  **说明**：经评估后沿用 Spring AI MCP Server（SSE）；`application.yml` 中 `spring.ai.mcp.server.*` 与 `KIWI_AI_MCP_LOOPBACK_BASE_URL` 已配置。

- [x] 2.2 OpenAPI 工具注册由 Java 配置完成（非 `plugin.mcp.*`）  

  **说明**：`KiwiOpenApiSyncMcpToolsConfiguration` 扫描 `com.kiwi.project` 下 `@RestController`，要求 `@Operation(operationId + summary)`；排除 `AiChatCtl`、`.integration.` 包。

## 3. 安全与暴露范围

- [x] 3.1 业务 API 白名单扫描（`com.kiwi.project`），Camunda `/engine-rest` 不在包内故不暴露  

- [x] 3.2 MCP 与 REST 共用 Sa-Token  

  **说明**：外部 MCP 客户端须带 `Authorization: Bearer`；本机回环在 `KiwiLocalMcpClientConfiguration` 注入当前登录 Token。

## 4. 移除 @Tool 并补齐 OpenAPI

- [x] 4.1 系统域控制器：移除 MCP 用 `@Tool`，补齐 `@Tag` / `@Operation(operationId, summary)`
- [x] 4.2 工具域控制器：同上
- [x] 4.3 BPM 域控制器：同上
- [x] 4.4 其他（Monitor、Notification 等）：同上
- [x] 4.5 非 REST 助手动作：`AssistantNavigationTools` / `AssistantDesignerTools` 保留 `@Tool`；**不**再经 `AssistantActionsCtl` REST 暴露  

  **后注（2026-06）**：`assistant_*` **不**并入 MCP `tools/list`；由 `KiwiAssistantInProcessToolsFactory` 仅挂在 `kiwiChatClient` 进程内，与 `AssistantClientActionContext` 同线程登记 actions。

## 5. Spring AI 助手集成

- [x] 5.1 删除 `KiwiToolCallbackConfiguration`；统一 `kiwiChatClient`（`KiwiAdminAiMcpConfiguration`）  

  **说明**：业务工具 = `SyncMcpToolCallbackProvider(kiwiLocalMcpSyncClient)`（与对外 MCP 同源）；助手动作 = `KiwiAssistantInProcessToolsFactory`。已移除 `BpmDesignerAiConfiguration` 独立 ChatClient。

- [x] 5.2 `SYSTEM_PROMPT` 与 `operationId` / `assistant_*` 工具名对齐

- [x] 5.3 助手工具面与 MCP 业务工具契约一致  

  **说明**：业务工具经 MCP 回环对齐；`assistant_*` 仅助手可见（设计使然）。编译通过；对话 smoke 为运维验收项。

## 6. 文档与验收

- [x] 6.1 约定写入 `AGENTS.md`、`.cursor/rules/java-openapi-annotations.mdc`（`operationId` 必填、MCP 扫描类名）  

  **说明**：BREAKING 为控制器 `@Tool` → `@Operation(operationId)`；MCP 连接 URL/协议未变。

- [x] 6.2 `mvn -pl kiwi-admin/backend -am compile -DskipTests` 通过
