# Tasks

## 1. Module — done

- [x] kiwi-bpmn-designer-agent pom + EditPlan models
- [x] EditPlanApplicator + PlanSkipEvaluator
- [x] DesignerAgentOrchestrator + DesignerAgentPlanGenerator

## 2. Backend — done

- [x] DesignerAgentConfiguration (designerAgentChatClient)
- [x] DesignerAgentSessionService + DesignerAgentCtl SSE
- [x] application.yml config (`kiwi.bpm.designer-agent.*`)

## 3. Frontend — done

- [x] bpm-designer-agent component + SSE service
- [x] bpm-editor 入口切换（替换 bpm-ai-chat）

## 4. Tests — done

- [x] EditPlanApplicatorTest
- [x] Agent SSE 集成测试（`DesignerAgentOrchestratorSseTest`）
- [x] 手工验收 A1–A9（`MANUAL_ACCEPTANCE.md`）

## 5. Deprecation — done

- [x] 设计器 UI 入口切换
- [x] `@Deprecated` write-workflow 设计器桥接（Ctl/Orchestrator/Session/Intent/DesignerTools/tryWriteWorkflow）
- [x] 删除 bpm-ai-chat、`AiWriteWorkflowService`、`bpm-designer-assistant.handlers.ts`
- [x] designer-agent.enabled 时禁用 `tryWriteWorkflow`

## 6. Follow-up — partial

- [ ] OpenSpec capability spec（A1–A10 scenarios）
- [x] tool_start/end advisor 细粒度轨迹
- [x] confirm-plan 后 SSE 续推（`/runs/{id}/stream/resume` + 前端续订）
- [ ] await_install 前端卡点
- [ ] EditPlan 黄金用例扩展
- [ ] Mongo 持久化 run
