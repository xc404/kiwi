## 1. Dependencies

- [x] 1.1 Add `spring-ai-alibaba-graph-core` (2.0.0-M1.1 line) to `kiwi-bpmn-designer-agent`
- [x] 1.2 Exclude Jackson 2 from graph-core; compile against Boot 4 Jackson 3 / Spring AI 2.0.0-M6

## 2. Graph runtime

- [x] 2.1 Define OverAllState keys and project to `DesignerAgentRun`
- [x] 2.2 Implement nodes: ingest, generate, explain, apply, validate, persist-preview intent
- [x] 2.3 Wire StateGraph edges, interruptBefore human gates, compile with CheckpointSaver
- [x] 2.4 Replace `DesignerAgentOrchestrator` façade with Graph factory + node handlers

## 3. Session and checkpoint

- [x] 3.1 Shrink `DesignerAgentSessionService` to Graph start/resume/status; remove `pendingContinuations`
- [x] 3.2 Keep SSE sink map only; keep CTL HTTP/SSE contract
- [x] 3.3 Implement MemorySaver and MongoCheckpointSaver with `kiwi.bpm.designer-agent.checkpoint`

## 4. Tests

- [x] 4.1 Rewrite SSE tests against CompiledGraph interrupt/resume
- [x] 4.2 Cover reject-plan regenerate and answer resume
