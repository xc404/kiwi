## Why

全局「Kiwi · AI」聊天已支持 `POST /ai/assistant` 返回的 `actions`，但前端仅在 `ChatComponent` 内用硬编码分支处理 `navigate`，无法按页面扩展其它客户端动作，也不利于与「服务端 MCP 工具只做真实业务」的分层叙述对齐。需要在不改动后端契约的前提下，把客户端动作变成可插拔、可组合的扩展点。

## What Changes

- 引入 **助手客户端动作（Client actions）** 的统一编排：责任链 / 策略式 `AssistantActionHandler`，默认内置 `navigate` 处理。
- `ChatComponent` 支持通过 **`input()`** 注入每页（或每处嵌入）的额外 handler；编排顺序与「未识别动作」行为在设计与 spec 中写明。
- （可选）提供 `InjectionToken` 多提供者入口，供在 `ApplicationConfig` 或路由级注册全局/模块级 handler，与 `input` 合并规则见 `design.md`。
- **不**将 UI 手势改为 MCP Tool；后端 `AiAssistantResponse` 与 MCP 工具边界保持不变。

## Capabilities

### New Capabilities

- `admin-ai-client-actions`: 定义助手返回的 `actions` 在前端的编排契约、默认 `navigate` 行为、扩展方式与向后兼容要求。

### Modified Capabilities

- （无）`openspec/specs/` 下无既有 admin-ai 能力文档；本 change 仅新增能力 spec。

## Impact

- **前端**：`kiwi-admin/frontend` 新增小模块（handler 接口、默认 navigate 实现、编排服务），`chat.component.ts` 改为调用编排层。
- **后端**：无 API 变更。
- **文档**：本 change 的 `design.md` / `spec.md`；可选在 `frontend/README.md` 补一句嵌入用法（若 tasks 包含）。
