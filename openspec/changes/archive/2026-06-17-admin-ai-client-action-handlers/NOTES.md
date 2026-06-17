# 归档说明（spec 过时，不同步入 main）

**日期：** 2026-06-17

本 change 的前端编排层（`shared/ai-assistant` + `ChatComponent.actionHandlers`）**已在代码库落地**，但 delta spec / design 与当前系统契约不一致，**归档时跳过 spec 同步**（`openspec archive --skip-specs`）。

## 为何 spec 过时

1. **动作 DTO 形状**：spec 场景仍写 `navigate` 带顶层 `path`；实际前后端统一为 `ClientAction` / `AiClientAction`：`{ type, params }`（如 `params.path`、`params.queryParams`）。见 `ClientAction.java`、`ai-chat.service.ts`。
2. **动作类型扩展**：除 `navigate` 外，已实现 `toolbar`、`bpmnXml`、`appendComponent`、`matchComponent`（BPM 设计器 handlers 在 `bpm-designer-assistant.handlers.ts`），本 change spec 未覆盖。
3. **Handler 接口**：design 写 `handle` 无返回值；实现为 `handle(...): boolean`（`true` 时截断后续 action 队列）。
4. **后端/MCP 叙事**：`assistant-client-actions-tool-exception` 已决：助手动作经 `AssistantNavigationTools` / `AssistantDesignerTools`（`@Tool`）登记，非本 change proposal 撰写时的 `AssistantActionsCtl` 路径。
5. **BPM 集成**：design 将 BPM 接线列为 non-goal；现通过统一 `POST /ai/assistant` + 页面级 `actionHandlers` 完成（`bpm-editor-ai-assistant` 另有独立 change，其专用 `/ai/bpm-designer` 任务亦未按原 spec 实现）。

## 若需正式 main spec

应新开 change，基于当前 `ClientAction` 类型表与编排语义重写 `admin-ai-client-actions`（或合并进 BPM AI 能力 spec），而非同步本目录下的 delta。
