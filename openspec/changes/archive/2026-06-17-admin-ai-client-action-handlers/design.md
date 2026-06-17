## Context

- 后端 `AiAssistantResponse` 已包含 `actions`（当前主要为 `type: navigate` + `path` + `queryParams`），菜单助手工具在服务端执行数据类操作后通过 `MenuAssistantActionContext` 登记跳转等客户端指令。
- 前端 `ChatComponent` 在 `applyAssistantActions` 中硬编码处理 `navigate`，且处理完第一个 `navigate` 后即停止（与历史实现一致），嵌入位置仅有主布局浮窗与仪表盘 `AiChatComponent`。

## Goals / Non-Goals

**Goals:**

- 抽出 **客户端动作编排层**：可测试、可扩展的 handler 链，默认提供 `navigate`。
- `ChatComponent` 支持 **每嵌入点** 附加 handler（`input`），并与 **可选的全局多提供者** 合并，便于未来路由/模块注册额外类型。
- 明确 **编排顺序** 与 **未识别 action** 行为，避免 silent failure。

**Non-Goals:**

- 不修改后端 DTO、不新增 MCP Tool 类型、不合并 BPM 设计器专用 `BpmDesignerAssistantResponse`（仍为独立 change）。
- 不在本 change 内为 BPM 页接线 `bpm-designer` API。

## Decisions

1. **Handler 接口（策略 + 链）**  
   - 每个 handler 实现 `supports(action): boolean` 与 `handle(action, ctx): void`（同步即可）。  
   - **Rationale**：与现有 `navigate` 分支等价，避免引入 Observable 除非未来需要异步副作用。

2. **编排顺序：input → 多提供者 → 内置**  
   - `actionHandlers`（`input`）最先匹配，便于页面覆盖或抢占特定 `type`。  
   - 其次 `InjectionToken<AssistantActionHandler>` 且 `multi: true`（可选注册）。  
   - 最后内置 `NavigateAssistantActionHandler`。  
   - **Rationale**：页面显式传入优先级最高；全局扩展次之；默认兜底最后。

3. **`AssistantActionOrchestratorService`（`providedIn: 'root'`）**  
   - 在 `dispatch(actions, inputHandlers)` 内合并链并遍历 `actions`。  
   - 对每个 action，沿链找到第一个 `supports` 为真的 handler 并 `handle`。  
   - **与旧行为对齐**：若某条 action 被 **navigate** handler 成功处理（存在有效 `path` 并已触发 `router.navigate`），则 **不再处理后续 action**（保持原先 `break` 语义）。若未来存在非 navigate 且需多条连续执行，可在后续 change 放宽。  
   - **Rationale**：零行为回归风险；spec 中写明。

4. **未识别 `type`**  
   - `dispatch` 在开发态可 `console.warn`；生产不抛错，避免打断用户阅读模型回复。  
   - **Rationale**：助手可能逐步扩展 action 类型，旧前端应降级为仅展示文本。

5. **文件位置**  
   - `kiwi-admin/frontend/src/app/shared/ai-assistant/`：接口、默认 navigate handler、orchestrator、`ASSISTANT_ACTION_HANDLERS` token。  
   - **Rationale**：与 `chat` 组件同层可发现性；不放入 `core` 以免与 HTTP 服务混淆。

## Risks / Trade-offs

- **[Risk] 编排顺序误用导致页面劫持 navigate**  
  - **Mitigation**：文档说明仅在需覆盖时使用 `input` 高优先级 handler；默认仍由内置 navigate 处理标准 `navigate`。

- **[Risk] multi token 未注册时 `inject` 行为**  
  - **Mitigation**：使用 `inject(TOKEN, { optional: true }) ?? []`；不在根强制注册空 multi。

- **[Trade-off] 仅同步 handler**  
  - 异步 UI（如 `Observable`）留待后续需求再扩展接口。

## Migration Plan

- 纯前端增强；部署无数据迁移。回滚即还原 `ChatComponent` 与删除 `shared/ai-assistant` 目录。

## Open Questions

- 无（若产品要求「多条 navigate 依次执行」再开 change 修改编排语义）。
