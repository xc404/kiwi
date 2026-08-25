## Context

Designer Agent 已交付 EditPlan + SSE：`DesignerAgentOrchestrator` 手写 ingest → generate → await_plan → apply → validate → preview/ask/install；`DesignerAgentSessionService` 用内存 Map 存 `DesignerAgentRun`，用 `pendingContinuations` + `@Async` 做人机续跑。前端协议稳定。编排与会话存储耦合，重启丢失，多实例无法共享。

约束：Boot 4、Jackson 3、Spring AI 2.0.0-M6、Java 25；`kiwi-bpmn-designer-agent` 不得依赖 admin DAO。

## Goals / Non-Goals

**Goals:**

- 用 Alibaba Graph 维护阶段关系与 HITL interrupt/resume。
- Checkpoint（memory / Mongo）作为 run 状态真相源。
- SessionService 仅作 façade + SSE sink + preview 写库。
- REST/SSE 对前端兼容。

**Non-Goals:**

- 全局助手 ChatMemory / Graph 化。
- Temporal、Dify、Agent Framework ReactAgent。
- `await_install` 前端与新 REST。
- 将整仓 Jackson 降回 2.x。

## Decisions

1. **依赖**：`spring-ai-alibaba-graph-core` 2.0.0-M1.1 线，只加 designer-agent 模块；不引入 DashScope starter。LLM 仍走现有 `DesignerAgentPlanGenerator` + `designerAgentChatClient`。
2. **Jackson**：排除 graph-core 自带 Jackson 2，使用 Boot 4 Jackson 3；编译失败则记录阻塞，不回退 Jackson。
3. **threadId = runId**：与现有 API 对齐。
4. **DesignerAgentRun 保留为投影**：SSE 缓冲与测试视图，由 Graph `OverAllState` 同步，避免一次性改前端/测试字段。
5. **写库在 façade**：preview 确认后 admin `SessionService` 调 DAO，图节点只置 `persistRequested` 之类标志。
6. **Checkpoint**：`kiwi.bpm.designer-agent.checkpoint=memory|mongodb`，默认 mongodb；本地可 memory。
7. **SSE Map 允许保留**：仅 `runId → Consumer`，不算编排状态。

## Risks / Trade-offs

- [graph-core 与 Jackson 3 / Spring AI M6 不兼容] → 先 spike compile；失败停止并文档化。
- [checkpoint 体积含 XML/事件] → Mongo 文档限制需裁剪 events 或只存关键字段。
- [Graph stream 事件与现有 AgentStreamEvent 不一致] → 节点内继续 `run.emit`，不把 Graph 原始 output 直接给前端。

## Migration Plan

- 默认开关仍 `kiwi.bpm.designer-agent.enabled=false`。
- 无 API 迁移；回滚为恢复 Orchestrator 提交（无数据格式对前端）。
- Mongo 集合 `designer_agent_checkpoint` 可废弃。

## Open Questions

- graph-core 的 `CheckpointSaver` 接口名与 `interruptBefore` API 以 spike 源码为准。
