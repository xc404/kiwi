## Why

Kiwi 已将 MCP 业务工具改为 OpenAPI（`@Operation.operationId`）驱动，并归档 `swagger-mcp-plug-server2mcp-webmvc`。后续评估 [infobip-openapi-mcp](https://github.com/infobip/infobip-openapi-mcp) 作为增强方案时，发现其官方基线为 **Spring Boot 3.5.x + Spring AI 1.1.x**，与 Kiwi 当前 **Spring Boot 4 + Spring AI 2.0.0-M6** 不兼容，贸然引入存在 BOM 冲突与运行时风险。需要以 OpenSpec 正式记录「等待 infobip 版本对齐」的决策、过渡期策略与重新评估门槛，避免重复讨论或误接入。

## What Changes

- **不引入** `infobip-openapi-mcp` 依赖；维持现有 `KiwiOpenApiSyncMcpToolsConfiguration` + Spring AI MCP Server + 本机 SSE 回环架构（见 `openspec/specs/admin-mcp-openapi/spec.md`）。
- 在 change 中定义 **infobip 采纳门槛**（版本、PoC 验收、与 Kiwi 硬约束符合度）及 **定期复查** 流程。
- 过渡期 MCP schema 增强优先走 **自研路径**（从 Springdoc OpenAPI JSON 生成 `inputSchema`，保留 `MethodToolCallback` 直调）；该实现可作为独立 change，本 change 仅引用为 interim，不阻塞等待。
- 当 infobip 发布支持 Kiwi 技术栈的版本时，再开 **新 change** 做 PoC/迁移；本 change 归档时更新 `NOTES.md` 记录结论。

## Capabilities

### New Capabilities

- `admin-mcp-infobip-gate`：infobip-openapi-mcp 采纳门槛、复查节奏、PoC 验收清单；明确在门槛满足前 SHALL NOT 合并 infobip 依赖。

### Modified Capabilities

- `admin-mcp-openapi`：补充过渡期自研 schema 增强要求与「第三方 OpenAPI→MCP 框架」采纳的前置条件（不改变当前已落地的 MCP 架构要求）。

## Impact

- **代码**：本 change 阶段 **无强制代码变更**；仅文档与跟踪任务。
- **依赖**：`kiwi-admin/backend/pom.xml` 不新增 `com.infobip.openapi.mcp`。
- **相关类**（维持现状）：`KiwiOpenApiSyncMcpToolsConfiguration`、`KiwiAdminAiMcpConfiguration`、`KiwiLocalMcpClientConfiguration`、`KiwiAssistantInProcessToolsFactory`。
- **路线图**：`.cursor/plans/kiwi_optimization_roadmap` 中「MCP 演进」项与本 change 对齐——infobip 待版本，interim 为自研增强。
