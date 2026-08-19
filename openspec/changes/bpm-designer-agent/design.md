# Design: BPM 设计器 Agent

## Architecture

ReAct Agent + EditPlan patch + MCP 白名单 + SSE 事件流。

## Key types

- `EditPlan` / `EditOperation` — 有序变更
- `AgentStreamEvent` — stage / plan_ready / preview_ready / done
- `DesignerAgentOrchestrator` — ingest → generate → await_plan → apply → validate

## Config

`kiwi.bpm.designer-agent.enabled=false` 默认；启用：`KIWI_BPM_DESIGNER_AGENT_ENABLED=true`
