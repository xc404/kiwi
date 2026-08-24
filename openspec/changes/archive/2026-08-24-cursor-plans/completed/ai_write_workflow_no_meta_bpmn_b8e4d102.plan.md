# Plan：AI 写工作流去掉元 BPMN → 意图分派 + Java 管线 + 会话状态

## 命名约定（禁止再用 authoring）

| 角色 | 新名 | 说明 |
|------|------|------|
| 产品能力 | **AI 写工作流** | UI / 文档用语 |
| 配置前缀 | `kiwi.ai.write-workflow` | 替代历史 `workflow-authoring` |
| REST | `/ai/write-workflow/**` | 替代历史 `/ai/workflow-authoring/**` |
| 编排器 | `WriteWorkflowOrchestrator` | Java 管线入口 |
| 会话 | `WriteWorkflowSession` | 人机停顿与续跑状态 |
| 会话服务 | `WriteWorkflowSessionService` | get / continue / confirm |
| 状态 DTO | `WriteWorkflowStatus` | 面板与 Chat 共用 |
| OpenSpec | `ai-write-workflow-session` | change 名 |
| 流程定义 key（仅历史） | `kiwi_ai_workflow_authoring` | **只作为待拆除遗留名出现，新代码禁止沿用** |

下文除「迁移/拆除遗留」外，一律用上表新名。

## 背景与目标

当前用 Operaton 元流程（遗留 key `kiwi_ai_workflow_authoring`）编排「抽词 → Catalog → 生成 → 校验 → REPAIR/INSTALL/ASK → 预览 → 保存」。设计器 Chat 是多轮对话，桥接层却做成「每条用户消息 cancel+start 新实例」，与会话模型冲突。

**目标**：对齐开源 [jtlicardo/bpmn-assistant](https://github.com/jtlicardo/bpmn-assistant)——

- 入口用 **意图分类**（`talk` | `modify`）分派
- 改图走 **Java 管线**（保留 Plan IR / Catalog / Validator / Compiler）
- 人机停顿用 **`WriteWorkflowSession`**，不用元流程 User Task
- **不按自然语言语种**拆多套管线；create/modify 看是否有底图

**非目标（本期）**：

- 不上 Dify / LangGraph
- 不改 Plan IR 契约与确定性编译器核心逻辑
- 不强求 dogfood「元流程也是 BPMN」

## 目标架构

```
设计器 Chat 消息
       │
       ▼
AiAssistantService
       │
       ├─ 若 session.stage ∈ {await_ask, await_preview, await_install}
       │     → correlate：续跑 / 确认 / 拒绝
       │
       └─ 否则 → determineIntent(message_history)
              ├─ talk  → 现有 ChatClient + MCP 路径
              └─ modify→ WriteWorkflowOrchestrator.runTurn(...)
                            extract → catalog → generate → validate
                            switch(dispatchCode):
                              PASS    → stage=await_preview（或 autoSave→save→done）
                              REPAIR  → repair 循环（≤ max-repair-rounds）
                              INSTALL → stage=await_install
                              ASK     → stage=await_ask
```

Create vs Modify：由 `baseBpmnXml` / 画布是否为空决定（现有 Generate 已支持）；意图层不必再拆 `create`/`edit`。

## 决策摘要

| ID | 决策 |
|----|------|
| D1 | **废弃元流程运行时**：不再部署/启动遗留 BPMN；编排改为 `WriteWorkflowOrchestrator` |
| D2 | **意图二分类**：LLM `{intent: talk\|modify}`；可附短规则兜底 |
| D3 | **Session 存状态**：按 `targetProcessId`（+ 可选 `userId`）持久化 stage、candidateXml、catalog、issues、askMessage、repairRound 等 |
| D4 | **人机确认走 Chat/面板 API**：`answer` / `confirmPreview` / `confirmInstall`，不再 `taskService.complete` |
| D5 | **Delegate 逻辑下沉**：Extract/Catalog/Generate/… 改为无 `DelegateExecution` 的 step 服务 |
| D6 | **Feature flag**：迁到 `kiwi.ai.write-workflow.enabled`；遗留 `workflow-authoring` 配置读一期兼容后删除 |

## 现状映射（保留 / 替换）

| 现有 | 处置 |
|------|------|
| `AssistantPlanGenerateService`、Validator、Compiler、`AssistantBpmnToPlan`、Rules | **保留** |
| `AssistantCatalogContextBuilder`、SPI | **保留** |
| `Assistant*Delegate` | **抽到 Orchestrator/Step**；Delegate 删除或仅过渡壳 |
| `AssistantProcessService` | **替换**为 `WriteWorkflowSessionService` + `WriteWorkflowOrchestrator` |
| 遗留 BPMN + `AssistantProcessDeployer` | **停用部署**；文件归档到 `docs/ai/` 或删除 |
| 遗留 `BpmnAssistantCtl` `/ai/workflow-authoring/**` | **迁移**到 `/ai/write-workflow/**`；去掉 taskId complete |
| `AiAssistantService` 中「凡设计器会话都 start」 | **改为** intent→modify 才进管线；await_* 先 correlate |
| 前端 `bpm-ai-chat` 面板 | **保留 UX**；对接 `WriteWorkflowStatus`；换 continue/confirm API |
| `AssistantVariables` stage/dispatch 常量 | **保留语义**，作为 session 字段名（类可改名为 `WriteWorkflowVars`） |

## 实施任务

### Phase 0 — 规格与契约（0.5d）

- [x] 以 plan 冻结意图 / session / correlate（本文件）
- [x] 冻结对外 DTO：`WriteWorkflowStatus`
- [x] 明确 correlate 规则表

### Phase 1 — Session + Orchestrator（后端）

- [x] `WriteWorkflowSession`
- [x] `WriteWorkflowOrchestrator`
- [x] `WriteWorkflowSessionService`
- [x] 去掉引擎 180s 轮询
- [x] 单测 `WriteWorkflowOrchestratorTest`

### Phase 2 — 意图分派接入 `AiAssistantService`

- [x] `AssistantIntentService`
- [x] correlate → intent → modify 管线
- [x] 禁止「有 processId 就一律进写流」

### Phase 3 — API 与前端

- [x] `WriteWorkflowCtl` `/ai/write-workflow/**`
- [x] 前端 `AiWriteWorkflowService` + `bpm-ai-chat` stage 面板

### Phase 4 — 拆除元 BPMN 运行时

- [x] 删除 `AssistantProcessDeployer` / `AssistantProcessService` / 元 BPMN / 旧 Ctl
- [x] 配置迁到 `kiwi.ai.write-workflow.*`
- [x] README 更新

### Phase 5 — 回归与开关

- [x] 编译通过；`WriteWorkflowOrchestratorTest` 通过
- [ ] 手工多轮 / 闲聊（待本地联调）


**Correlate 规则**

| 当前 stage | 用户下一条消息 / 动作 | 行为 |
|------------|----------------------|------|
| `await_ask` | 非空文本（或面板提交） | 写入 `userAnswer`，从 generate 续跑 |
| `await_preview` | 确认 | save → done |
| `await_preview` | 拒绝 | 带反馈再 generate，或结束 session |
| `await_install` | 接受 / 拒绝 | 安装后回 validate，或回 generate |
| `done` / 无 session | 新消息 | intent；modify 则新建 session 并 `runTurn` |
| 任意活跃 stage | 「重新开始」 | 关闭旧 session，新 `runTurn` |
| 非 await_* | 新 modify | 覆盖旧 session，新 `runTurn` |

## API 草图（目标态）

```http
GET  /ai/write-workflow/by-target?targetProcessId=
POST /ai/write-workflow/sessions/{sessionId}/confirm-preview   { "confirmed": true|false }
POST /ai/write-workflow/sessions/{sessionId}/confirm-install   { "accepted": true|false }
POST /ai/write-workflow/sessions/{sessionId}/answer            { "userAnswer": "..." }
# 主路径：POST /ai/assistant（intent + correlate）
```

`WriteWorkflowStatus`：`sessionId`、`stage`、`dispatchCode`、`candidateXml`、`assistantReply`、`askMessage`、`pluginHintJson`、`issuesJson`、`active`；**无** `tasks`。

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| LLM 耗时长堵 HTTP | 先同步到停顿点；后续可异步 job（仍非 BPMN） |
| 意图误判 | prompt 示例；失败偏 `talk` |
| await_ask 时用户发新需求 | correlate 前再意图，或面板强制「提交补充」 |
| 进程重启丢 session | v1 可接受；v1.1 Mongo |
| 旧路径/旧前端 | 双挂一期 + 同步发版 |

## 工作量粗估

| Phase | 粗估 |
|-------|------|
| 0 规格 | 0.5d |
| 1 Orchestrator | 2–3d |
| 2 Intent | 1d |
| 3 API+前端 | 1–2d |
| 4 拆除元 BPMN | 0.5d |
| 5 回归 | 1d |
| **合计** | **约 6–8d** |

## 建议落地顺序

1. Phase 1 Orchestrator 单测跑通  
2. Phase 2 接 `AiAssistantService`（消除每句盲 start）  
3. Phase 3 新 API + 前端  
4. Phase 4 删元流程部署与死代码  
5. 需要时再 OpenSpec propose / archive  

## 验收标准

- [ ] 多轮追问同一 `WriteWorkflowSession` 续跑，不 cancel 重建引擎实例
- [ ] 闲聊不触发写流管线
- [ ] modify：坏图不落盘；缺插件需确认；预览确认后才 save（autoSave 行为不变）
- [ ] 写工作流路径下不再产生 Operaton 元流程实例
- [ ] Plan IR → 编译 → 校验闭环测试通过
- [ ] 新增代码 / 配置 / API **无 authoring 字样**

## 参考

- 开源：`/determine_intent` → `talk`|`modify`；`/modify` 内 create/edit；无引擎编排
- 本文件：`.cursor/plans/ai_write_workflow_no_meta_bpmn_b8e4d102.plan.md`
- 将被取代的旧 design：`openspec/changes/ai-workflow-authoring-process/design.md`（历史 change 名保留，新 change 用 `ai-write-workflow-session`）
