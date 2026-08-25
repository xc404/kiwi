# BPM 设计器 Agent：统一对话框输入与会话续聊

**状态：阶段 1 + 阶段 2 已完成（2026-08-25）**  
**关联**：`kiwi-bpmn-designer-agent`、`kiwi-admin/frontend/.../bpm-designer-agent.component.ts`

---

## 产品原则（阶段 2 定稿）

1. **一流程一聊天窗口**：`targetProcessId` ↔ 单 runId，同时仅一个 open 会话。
2. **不主动结束会话**：保存 / explain 完成 → `await_follow_up`；仅用户「清空会话」释放 checkpoint。
3. **持久化 + 恢复**：Mongo `designer_agent_session`（索引 + messages）+ Graph checkpoint；打开设计器 `GET by-target` 恢复。

---

## 阶段 2 实施摘要

### Graph
- [x] `finish` → `human_follow_up` interrupt
- [x] `conversationHistory` state key
- [x] `resumeAfterFollowUp` → ingest

### Session / API
- [x] `POST /runs/{id}/follow-up`
- [x] `POST /sessions/clear?targetProcessId=`
- [x] `DesignerAgentSessionStore`（Mongo）
- [x] `createRun` 仅无会话时；不再 `clearByTarget` 取代旧 run

### 前端
- [x] `send()`：`await_follow_up` → followUp
- [x] 打开流程恢复 `messages`
- [x] 「清空会话」按钮

### 测试 / 文档
- [x] `DesignerAgentOrchestratorSseTest` follow-up 路径
- [x] `kiwi-bpmn-designer-agent/README.md` 状态机更新

---

## 统一 `send()` 决策树（当前）

```text
if busy → return
if await_plan → planFeedback(text)
if await_preview → previewFeedback(text)
if await_ask → answer(text)
if has runId && canFollowUp → followUp(text)
else → createRun(text)   // 仅该流程尚无会话
```

---

## 参考文件

| 文件 | 说明 |
|------|------|
| `DesignerAgentSessionService.java` | followUp、clearSession、持久化 |
| `DesignerAgentSessionStore.java` | Mongo 会话文档 |
| `DesignerAgentGraphFactory.java` | human_follow_up 节点 |
| `bpm-designer-agent.component.ts` | follow-up / 恢复 / 清空 |
