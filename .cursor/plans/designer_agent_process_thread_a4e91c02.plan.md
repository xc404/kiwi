# Designer Agent：按流程的 AI 会话历史

> 讨论草案（未实施）。与 `AiChatConversation` / Spring `ChatMemory` 解耦；与 Graph checkpoint（进行中的 run）分层。

## 现状缺口

- 每次 `send()` 开新 `run`，`clearByTarget` 丢掉上一 run。
- 前端 `messages` 仅内存，刷新即空。
- LLM prompt 只有「本轮 scenario + XML + userAnswer」，没有「这个流程上我们已达成的约定」。
- 全局 `ai_chat_conversation`（`scope=bpm-designer`）是旧聊天框 transcript，消息形态与 Agent（Plan / SSE / HITL）不匹配。

## 分层（不要合成一层）

```
流程 BPMN XML                    ← 画布真相（已应用的编辑）
        │
        ▼
ProcessAgentThread               ← 按 (ownerId, processId) 的会话壳
  · digest（结构化工作记忆）
  · items（给人看的时间线）
  · activeRunId（进行中闸门，可空）
        │
        ├── DesignerAgentRun / Graph checkpoint  ← 本轮 HITL，短命
        └── ChatMemory?（可选实现细节）          ← 仅「最近 N 条 user/assistant」
```

| 层 | 生命周期 | 给谁 | 不存什么 |
|----|----------|------|----------|
| BPMN | 与流程同寿 | 模型 + 人 | 聊天 |
| Thread + digest | 与「该用户在此流程上的 Agent 协作」同寿 | 人看时间线；模型只吃 digest + 近 N 轮意图 | 完整 thinking / 全量 tool dump |
| Run checkpoint | 从 start 到 done/error | 续 SSE、confirm-plan | 跨天闲聊 |

**不**把 Thread 做成 ChatMemory 的别名；**不**复用 `AiChatConversation` 当 Agent 时间线。

## 标识与隔离

v1 默认：**一条线程 = `(ownerId, targetProcessId)`**。

- 打开流程 → 加载（或惰性创建）该用户在此流程上的线程。
- 多人编同一流程：各自线程；**已落地的改图只信 XML**，不把别人的闲聊喂给模型。
- 审计可选第二通道：process 级 activity log（谁确认了哪次 preview），不做 LLM 上下文。

v2 再考虑同一流程多线程（「方案 A / 方案 B」），不要在 v1 做会话下拉。

## 时间线条目（给人，不是 raw Message）

每条 `item` 带 `runId`、时间、类型：

| type | 内容 |
|------|------|
| `user_intent` | 用户输入的 scenario |
| `ask_answer` | await_ask 的问答 |
| `plan` | 展示用 summary + 是否确认/跳过/拒绝（不要默认存完整 EditPlan JSON 进时间线；完整 JSON 挂 run 或附件） |
| `assistant` | 面向用户的回复 |
| `applied` / `discarded` | preview 结果 |
| `error` | 失败摘要 |

Thinking / tool_start 默认折叠或只留最近一轮；持久化可只留 `thinking` 截断字段，避免撑爆 Mongo。

## 模型上下文（工作记忆，不是全文回放）

每次 `runTurn` 组装：

1. **当前** `baseBpmnXml` + 选中元素（必须）。
2. **Thread.digest**（短结构化 JSON，例如）：
   - `lastAppliedSummary`
   - `rejectedDirections[]`（用户否决过的方向）
   - `openQuestions[]`
   - `prefs`（命名、禁止组件等，显式写出的才记）
3. **最近 K 条** `user_intent` + 对应 `assistant`/`plan.summary`（K≈4–8），**禁止**把整个 items 数组当 ChatMemory 窗口灌进去。
4. 本 run 的 `issuesJson` / `userAnswer`。

Digest 更新时机：preview 确认写入流程后、用户明确否定 plan、ask 闭环。用一次小 LLM 或规则拼接均可；规则优先，避免每轮再总结烧 token。

## API 形状（挂在 `/bpm/designer-agent`，不进 `/ai/conversations`）

- `GET .../threads/by-target?targetProcessId=` → 线程头 + digest + `activeRunId`
- `GET .../threads/{id}/items?before=&limit=` → 时间线分页
- 现有 `POST .../runs/stream`：服务端 **append** `user_intent`，run 结束再 append plan/assistant/applied
- 前端发 run **仍只带本轮 scenario + 当前 XML**，不回传全历史

`startRun` 的 `clearByTarget` 只清 **active run**，不清 Thread。

## 与现有构件的关系

| 构件 | 关系 |
|------|------|
| `AiChatConversation` | 不复用。scope `bpm-designer` 留给旧助手或废弃，避免两套 UI 抢同一文档。 |
| Spring `ChatMemory` | 非必须。若用，`conversationId = threadId`，且只镜像「近 K 条意图」，真相仍是 Thread。 |
| Graph checkpoint | 只管进行中 run；done 后投影进 items + 刷新 digest，checkpoint 可删可归档。 |
| `DesignerAgentSessionService` | façade：start/resume + 写 Thread；编排仍不依赖 admin DAO（Thread 持久化放 admin，模块边界与 checkpoint 方案一致）。 |

## 前端

- 打开设计器：按 `bpmProcessId` 拉 items，填面板时间线。
- 进行中 run：SSE 照旧；重进页面用 `activeRunId` + resume stream。
- 不必做「多会话切换」（v1）。

## 刻意不做

- 用 ChatMemory 当唯一存储。
- 把 thinking/MCP 全量当历史喂模型。
- 跨流程共享同一 Agent 线程。
- 把全局助手消息与设计器 Agent 合成一个 conversation。

## 落地顺序

1. Thread 文档 + items 追加 + 打开流程加载时间线（先解决「刷新丢聊天」）。
2. Prompt 注入 digest + 近 K 轮意图。
3. 与 Graph checkpoint 对齐 `activeRunId`（进行中闸门）。
4. 再评估要不要 ChatMemory 做 K 窗实现。
