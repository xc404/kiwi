# 归档说明

**日期：** 2026-06-17

## 相对初稿的演进

| 初稿（proposal/design） | 落地 |
|-------------------------|------|
| `com.ai.plug:server2mcp-starter-webmvc` | **未引入**（传递依赖 SNAPSHOT 不可解析） |
| 移除 `spring-ai-starter-mcp-server-webmvc` | **保留** Spring AI MCP Server（SSE） |
| server2mcp 桥接 ChatClient | **本机 MCP 回环**（`KiwiLocalMcpClientConfiguration` + `SyncMcpToolCallbackProvider`） |
| `assistant_*` 合并进 MCP tools/list | **仅进程内**（`KiwiAssistantInProcessToolsFactory` → `kiwiChatClient`） |
| `KiwiOpenApiDocumentedToolCallbackConfiguration` | 重命名为 **`KiwiOpenApiSyncMcpToolsConfiguration`** |
| 多个 ChatClient（如 `BpmDesignerAiConfiguration`） | 统一 **`kiwiChatClient`** |

## 核心类

- `KiwiOpenApiSyncMcpToolsConfiguration` — MCP 业务工具（`@Operation.operationId`）
- `KiwiAdminAiMcpConfiguration` — `kiwiChatClient` 组装
- `KiwiAssistantInProcessToolsFactory` — 助手 `@Tool`（不进 MCP 列表）
- `KiwiLocalMcpClientConfiguration` — SSE 回环 + Bearer 注入

## Spec

归档前已修订 delta spec，去掉 server2mcp 与「assistant 并入 MCP」条款，与当前代码一致。
