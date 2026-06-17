# 决策：助手前端动作用 `@Tool` 窄例外

## 背景

历史变更 `swagger-mcp-plug-server2mcp-webmvc` 要求 MCP/助手工具以 **OpenAPI 为主源**，并曾通过 `AssistantActionsCtl` 将 `assistant_navigate` 等以 REST + `@Operation` 暴露，以便与 Springdoc 同源。

## 现决（2026-05）

- **废止**「助手动作也必须经独立 REST 控制器」的做法：删除 `AssistantActionsCtl`，改为 **`AssistantNavigationTools` / `AssistantDesignerTools`（`@Tool`）** 按功能拆分注册。
- **保留**「业务域 API 仍以 OpenAPI 扫描为主」：`KiwiOpenApiSyncMcpToolsConfiguration` 继续从 `@RestController` + `@Operation` 生成业务工具。
- **合并**：MCP 工具列表 = OpenAPI 工具 + `MethodToolCallbackProvider.builder().toolObjects(assistantNavigationTools, assistantDesignerTools)` 合并结果，避免助手与 MCP 工具名分裂。

## Code Review 指引

- **允许**：仅在 **`AssistantNavigationTools`、`AssistantDesignerTools`**（及经评审、写入同一条款的同类 Bean）上使用 `@Tool` 做「actions 登记」类 glue。
- **不允许**：为绕过 OpenAPI 在其他业务 `Ctl`/`Service` 上恢复 MCP 专用 `@Tool`；新增业务 MCP 能力仍应走 REST + `@Operation`。

详见已更新的 `openspec/changes/swagger-mcp-plug-server2mcp-webmvc/specs/admin-mcp-openapi/spec.md` 末尾 **ADDED Requirements（窄例外）** 节。
