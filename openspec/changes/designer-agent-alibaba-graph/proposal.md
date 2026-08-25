## Why

Designer Agent 的 run 生命周期（HITL 闸门、异步续跑、SSE 绑定）目前用手写 `ConcurrentHashMap` + `pendingContinuations` 维护，无法断点续跑，也难以扩展分支。需要引入 Spring AI Alibaba Graph，用 `StateGraph` + interrupt/checkpoint 作为编排真相源。

## What Changes

- 将 `DesignerAgentOrchestrator` 的确定性阶段机改为 Alibaba Graph 节点与条件边。
- `DesignerAgentSessionService` 收缩为 Graph 入口（start / resume / attachStream / status），删除 `pendingContinuations`。
- 引入 `spring-ai-alibaba-graph-core`（2.0.x 线）与 `CheckpointSaver`（memory + Mongo）。
- HTTP/SSE 契约（`/bpm/designer-agent/**`、`AgentStreamEvent`）保持兼容，前端不改。
- 不引入 ChatMemory、Temporal、Agent Framework 高层 ReactAgent，不把 Graph 铺到全局助手。

## Capabilities

### New Capabilities

- `designer-agent-graph-runtime`: Designer Agent 使用 Graph 编排 run：节点流转、人机 interrupt/resume、checkpoint 持久化；对外 SSE/REST 行为与现网兼容。

### Modified Capabilities

- （无）仓库 `openspec/specs/` 中尚无 Designer Agent 能力规格。

## Impact

- 模块：`kiwi-bpmn-designer-agent`（图、节点、MemorySaver）、`kiwi-admin/backend`（Session façade、Mongo saver、写回 BPMN）。
- 依赖：`com.alibaba.cloud.ai:spring-ai-alibaba-graph-core`；须与 Boot 4 / Jackson 3 / Spring AI 2.0.0-M6 共存。
- API：无 **BREAKING**；`DesignerAgentCtl` 路径与 DTO 不变。
- 测试：`DesignerAgentOrchestratorSseTest` 改为对 `CompiledGraph` 做 interrupt/resume 事件断言。
