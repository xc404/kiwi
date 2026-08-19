---
name: Designer Agent Orchestrator 架构决策
overview: 评估 DesignerAgentOrchestrator 手写状态机 vs 成熟 Agent 编排框架；记录当前实现边界、行业对照、Kiwi 历史选择与演进路径。
todos:
  - id: keep-handwritten-v1
    content: v1 维持手写 Orchestrator + Stage Handler 拆分（不改框架依赖）
    status: pending
  - id: enhance-react-loop
    content: 增强 ReAct 内环：Spring AI multi-turn tool loop + thinking/tool SSE 细粒度事件
    status: pending
  - id: stage-handler-refactor
    content: 将 validateLoop / 人机卡点抽成独立 StageHandler，便于单测与扩展
    status: pending
  - id: evaluate-graph-migration
    content: Phase 2 若需断点续跑/多 Agent/可视化调试，评估 Spring AI Alibaba Graph 迁移
    status: pending
isProject: false
---

# DesignerAgentOrchestrator 架构决策

> **关联文档**  
> - Greenfield 总方案：[bpm_designer_agent_greenfield_13dbd3a0.plan.md](./bpm_designer_agent_greenfield_13dbd3a0.plan.md)  
> - 行业参考：[bpm_designer_agent_industry_reference.md](./bpm_designer_agent_industry_reference.md)  
> - OpenSpec design：`openspec/changes/bpm-designer-agent/design.md`  
> - 记录时间：2026-08-19

---

## 1. 问题

`DesignerAgentOrchestrator` 负责 Agent 功能编排。是否存在更成熟的通用实现可替代当前手写方案？何时应迁移？

---

## 2. 当前实现定性

**本质**：领域专用的**确定性状态机**（手写 `if/while` + `AgentRunStage`），**不是**通用 Agent 框架。

**主路径**（见 `DesignerAgentOrchestrator.java`）：

```
ingest → generate EditPlan → plan 闸门 → apply → validate → repair / install / ask / preview → done
```

| 职责 | 实现位置 | 说明 |
|------|----------|------|
| 阶段流转 | `DesignerAgentOrchestrator` | `emitStage` / `emitAwait` + stage 常量 |
| 人机卡点 | Orchestrator | `await_plan` / `await_preview` / `await_ask` / `await_install` |
| LLM + MCP 工具 | `DesignerAgentPlanGenerator` | Spring AI `ChatClient` 单次 prompt + tool calling |
| 事件流 | `DesignerAgentRun.emit` | `AgentStreamEvent`（stage / plan_ready / preview_ready / done 等） |
| 会话续跑 | `DesignerAgentSessionService` | `pendingContinuations` + `@Async executeAsync` |

**关键边界**：

- **ReAct 内环**（think → act → observe 多轮）不在 Orchestrator 层显式实现，而由 `ChatClient` 在 `PlanGenerator.generate()` 内隐式完成（通常一轮 prompt + 若干 MCP 调用 → 解析 JSON 为 `EditPlan`）。
- 与 Greenfield 计划中的 `DesignerAgentRuntime` 命名尚未完全对齐：实际交付为 `DesignerAgentOrchestrator` + `DesignerAgentPlanGenerator` 拆分。

---

## 3. 成熟替代方案对照

### 3.1 Spring AI Alibaba Graph（Java 版 LangGraph）— 首推框架选项

- **组件**：`StateGraph`、`CompiledGraph`、`OverAllState`、条件边、并行节点
- **Human-in-the-loop**：`interruptBefore` / `interruptAfter`，`updateState` 后继续 `stream`
- **能力**：checkpoint、持久化、流式、导出 PlantUML/Mermaid
- **优点**：复杂分支、断点续跑、多 Agent 拓扑更易维护
- **缺点**：新依赖（阿里生态）；需适配 Kiwi 的 `DesignerAgentRun` / SSE 协议；学习成本高于 ~270 行 Java

概念映射：

| 当前 Stage | Graph Node |
|------------|------------|
| ingest | `ingest` |
| think + MCP | `generate` |
| await_plan | `human_review`（interrupt） |
| apply | `apply` |
| validate + repair | `validate` + conditional edge |
| await_preview / install / ask | 各自 interrupt 节点 |

### 3.2 Spring AI Alibaba Agent Framework（更高层）

- 内置 `SequentialAgent`、`LoopAgent`、`RoutingAgent`、`ParallelAgent`
- 适合快速搭 ReAct / Plan-and-Execute；BPM 设计器强领域卡点仍需大量自定义节点

### 3.3 其他

| 方案 | 适用 | 对 Designer Agent |
|------|------|-------------------|
| **LangGraph4j** | LangGraph 语义移植 | 可行，生态弱于 Spring AI Alibaba |
| **Temporal.io** | 长事务、跨进程可靠编排 | 过重，人机卡点毫秒级交互不匹配 |
| **Spring State Machine** | 纯状态机 | 与现实现等价，仅换写法 |
| **Dify / 可视化 Agent 平台** | 低代码编排 | Kiwi 已明确不引入（见 `ai_workflow_verify_loop` plan） |

### 3.4 行业产品（闭源 Harness）

Cursor、GitHub Copilot、Windsurf Cascade 的 orchestration 均为**自研 Harness**，非基于 LangGraph/Dify。

行业共识（见 `bpm_designer_agent_industry_reference.md`）：

> **Harness 重于模型** — 投资编排运行时 + 事件协议，而非换模型。

---

## 4. Kiwi 项目内的历史选择

| 决策 | 出处 | 内容 |
|------|------|------|
| 确定性 Java 管线 | `ai_workflow_verify_loop` plan | 不用 Dify/LangGraph 可视化 Agent 工作流，不用开放 ReAct 自由选路 |
| 抛开旧 Orchestrator | Greenfield plan | `WriteWorkflowOrchestrator` 废弃，新建 `DesignerAgentOrchestrator` |
| 混合架构 | 行业参考 §4.3 | 外层 Plan-and-Execute（EditPlan + await_plan）+ 内层 ReAct（MCP）+ Reflexion（repair） |
| 同模式演进 | 代码 | 新旧 Orchestrator 均为 Java 状态机，领域模型从 Plan IR 换为 EditPlan |

**不引入通用框架的原因（v1）**：

1. 阶段相对固定，分支可枚举（plan / preview / ask / install / repair）
2. 与 EditPlan → Patch → Validator → Kiwi MCP/插件市场深度耦合
3. 前端 SSE 事件协议需与后端 stage 一一对应，可控性优先
4. 可测性：`DesignerAgentOrchestratorSseTest` 可 mock ChatClient 覆盖主路径

---

## 5. 决策结论

### 5.1 v1（当前阶段）：**维持手写 Orchestrator**

手写状态机在领域 Agent 中**并不落后**；Cursor/Copilot 同样自研 Harness。当前实现是正确权衡。

### 5.2 瓶颈不在 Orchestrator，而在 PlanGenerator

单轮 `ChatClient.call()` + tool calling 黑盒，导致：

- ReAct 多步未在 Orchestrator 层可观测
- `thinking_delta` / `tool_*` 事件粒度依赖 Advisor（`DesignerAgentToolTraceAdvisor`）补丁

优先增强 **ReAct 内环** 而非整体换框架。

### 5.3 触发 Graph 迁移的条件（Phase 2）

满足以下**任一**时再评估 Spring AI Alibaba Graph：

- [ ] Run **Mongo 持久化** + 服务重启后从 `await_plan` **断点续跑**
- [ ] 编排拓扑显著复杂化（Planner / Executor / Reviewer **多 Agent** 分工）
- [ ] 需要**可视化调试**（导出 Mermaid/PlantUML 给产品/运维）
- [ ] 条件分支超过手写 `validateLoop` 可维护阈值（>5 种 dispatch × 多轮 repair 组合爆炸）

---

## 6. 推荐演进路径

### 路径 A：最小改动（推荐近期）

1. **保留** `DesignerAgentOrchestrator` 作为对外 façade，SessionService 不变
2. **拆分 Stage Handler**（策略模式）：
   - `IngestStageHandler`
   - `GenerateStageHandler`
   - `ApplyValidateStageHandler`（含 repair loop）
   - `HumanGateStageHandler`（plan / preview / ask / install）
3. **增强 ReAct**：Spring AI multi-turn tool loop 或 Advisor 链，使 `tool_start/end`、`thinking_delta` 与 Orchestrator stage 对齐
4. 单测按 Handler 隔离，保留 `DesignerAgentOrchestratorSseTest` 端到端

### 路径 B：迁移到 Graph（Phase 2 备选）

1. 引入 `spring-ai-alibaba-graph-core`
2. 将 `DesignerAgentRun` 映射为 `OverAllState`
3. 人机卡点改为 `interruptBefore("await_plan")` 等
4. `DesignerAgentOrchestrator` 退化为 `CompiledGraph.invoke()` 的薄包装
5. SSE：订阅 Graph `stream()` 输出，映射为现有 `AgentStreamEvent` 类型（前端协议不变）

---

## 7. 与 Greenfield 计划差异备忘

| Greenfield 计划 | 实际 / 本决策 |
|-----------------|---------------|
| `DesignerAgentRuntime` 统一命名 | 拆为 `Orchestrator` + `PlanGenerator` |
| ReAct 多步显式循环 | v1 为 ChatClient 内隐式 tool loop |
| 不引入 LangGraph/Dify | **维持**；Graph 仅作 Phase 2 评估项 |
| Harness 投资 | **确认**：编排 + 事件协议是核心资产 |

---

## 8. 参考文献

- Spring AI Alibaba Graph：https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-graph-core
- Spring AI Graph Quick Start：https://java2ai.com/docs/frameworks/graph-core/quick-start
- GitHub Copilot Agent Loop：https://github.com/github/copilot-sdk/blob/main/docs/features/agent-loop.md
- Cursor Plan Mode：https://cursor.com/docs/agent/plan-mode
- ReAct vs Plan-and-Execute：https://buildingagenticai.com/blog/react-vs-plan-and-execute/
- Kiwi 行业参考：`.cursor/plans/bpm_designer_agent_industry_reference.md`

---

## 9. 文档维护

- 完成 Stage Handler 拆分或 Graph POC 后更新 §6 进度
- Camunda Copilot GA / Spring AI Alibaba Graph 大版本变更时修订 §3
- 与 OpenSpec `bpm-designer-agent` design.md 保持 stage 语义一致
