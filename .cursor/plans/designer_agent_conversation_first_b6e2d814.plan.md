# 设计器 Agent 重新设计：对齐 Cursor / Claude

**状态：草案（2026-09-15）**  
**对齐对象：** Cursor Agent / Claude Code — **对话线程 + 工具循环（harness）**，不是 StateGraph，也不是「generate→apply→validate」宿主状态机。  
**范围：** `kiwi-bpmn-designer-agent`、`kiwi-admin` Session/API、设计器前端 Agent 面板

---

## 为什么要重做

| 用户以为（Cursor/Claude） | kiwi 现状 |
|---------------------------|-----------|
| 上面几句模型都看见了 | 一条 `用户场景` 任务单 |
| 接着说 = 在当前工作区上继续 | 闸门 / follow-up / checkpoint 三套账 |
| 模型自己调工具改文件 | 宿主 Graph 强制 generate→plan 闸门→apply→validate→preview… |
| 会话 = messages | `conversationHistory` 给 UI，checkpoint 才是编排真相 |

Cursor **没有 Graph**。公开形态是 harness：`messages` 追加 → 模型说话或调工具 → 工具结果回 messages → 循环到停手 → 用户再 `send`。

kiwi 要对齐这个循环，而不是再画一张更小的流水线。

---

## 目标形态（同构）

```text
Session
  messages[]          // 唯一聊天真相，按角色进 Chat Completions
  workspace.bpmnXml   // 等价于 Cursor 已打开/已保存的文件
  candidateXml        // 本轮工具改出来的未确认缓冲（见预览）

用户 Enter
  → append user
  → workspace ← 当前画布（预览中即正在看的图）
  → running=true
  → loop:
        ChatClient(messages + tools)
        若 tool_calls → 执行工具，结果进 messages，继续
        否则 → append assistant 文本，停
  → 若工具改过图 → 画布预览（未确认缓冲）
     否则 → idle（只聊过天）
  → running=false
```

原则：

1. **模型决定下一步**（说话 / 查组件 / 改图 / 再改），宿主不预先路由 `explain | clarify | apply | ask`。
2. **改图必须走工具**，等价 Cursor 的 edit/apply_patch；禁止让模型直接吐 BPMN XML。
3. **宿主不在循环外再跑一遍 apply/validate 状态机。** 校验是工具返回值（改完立刻把 issues 给模型），模型要修就再调工具。
4. **没有** `await_plan` / `await_install` / `await_follow_up` / `await_ask` / `await_clarify` 作为编排阶段。问用户 = 助手气泡里问，下一句还是 `send`。
5. **预览回退** 是编辑器语义（丢掉未确认缓冲），不是 Graph interrupt，也不是第二次 LLM 分类。

---

## 人话：tool-calling loop 是什么

模型不会真的去改 BPMN。它只会**说话**。所谓工具，就是你事先给它的几个按钮：查组件、改图。它回复时可以写「请按这个按钮，参数是这些」；程序按按钮干活，把结果（成功、失败、校验问题）再塞回对话，然后**再问它一次**。这就是 loop：问 →（可能按按钮）→ 把结果告诉它 → 再问，直到它不再按按钮、只跟人说话。

对照 Cursor：你说「给这个类加日志」，它不是一次吐完整补丁就算完，而是可能先读文件、再改、再读报错、再改，直到它认为可以跟你说话了。中间每一步都是它自己点的，不是宿主写死「第 1 步读、第 2 步改、第 3 步校验」。

kiwi 里同一件事：

1. 你打字「加一个删除文件节点」。
2. 程序把聊天记录 + 当前图交给模型。
3. 模型可能先「查一下删除文件组件长什么样」（MCP），程序去查，把结果给它。
4. 它再「按改图按钮」，参数是 EditPlan；程序用现有 Applicator 改 XML，并把校验问题一并还回去。
5. 它看问题决定再改一次，或停下来跟你说「已经加上了」。
6. 这时循环结束。若图被改过，画布进入预览；你继续打字就是下一轮循环，点回退就是丢掉这次改的。

**不是：** 模型吐一篇 JSON，后台固定走 generate→apply→validate。那是宿主替它做决定。  
**是：** 查不查、改不改、改几次，都由模型在对话里点工具；程序只负责执行工具、把结果写进聊天。

Spring AI 的 ChatClient 打开 tool calling 后，3～5 往往在一次 `.call()` 里自动转完，聊天窗口里看起来仍是「你说一句、它忙一会儿、它回一句」。

### 它怎么知道要按「先读再改再看报错」这个顺序？

**不知道，也没有一张顺序表。** 「先读 → 改 → 看报错 → 再改」是旁观者事后串起来的样子，不是程序教给它的步骤清单。

每一次调用，模型只做**一个**决定：根据到目前为止的对话（你的话、已按过的按钮、按钮返回的结果），下一步是再按一个按钮，还是直接跟人说话。像跟人打电话：对方说完一句，你再决定是问一句还是动手查一下，没有人预先规定「第三句必须查文件」。

它之所以常常看起来很像那个顺序，是因为：

1. **你告诉它有哪些按钮、什么时候该用**（system + 工具说明）。例如：改图用 `apply_edit_plan`，返回值里有校验问题。
2. **上一轮结果就在对话里。** 刚改完图，返回「缺连线」，下一次它看见这句话，才更可能再按一次改图，而不是凭空「记得第三步是看报错」。
3. **训练时见过大量这种用法。** Cursor 的 Composer 还专门用强化学习练过「在真实环境里调工具」；普通 Chat Completions 模型弱一些，但格式一样：输出 tool call 或文本。

所以它**可能**先查再改，也可能一上来就改，也可能只问你一句不清楚的。没有保证。宿主要做的是限制最多按几次按钮（`maxToolRounds`），以及按钮本身可靠（Applicator / 校验），而不是写死顺序。

### 工具说明和 BPMN 校验，两件都要

不是二选一。它们管的是**不同时刻**：

| | 工具说明（含 system） | BPMN 校验 |
|--|----------------------|-----------|
| 何时起作用 | 模型**还没动手**，决定要点哪个按钮、参数怎么填 | 图**已经被程序改过**，告诉模型这次改得对不对 |
| 缺了会怎样 | 它可能在聊天里贴 JSON、乱调工具、或只说话不改图 | 它以为成功了，画布上却是坏图；下一轮也看不到「报错」 |
| kiwi 落点 | `apply_edit_plan` 的参数必须是 EditPlan schema；写清「改图只能调这个工具，禁止输出 XML」；MCP 工具说明要让它知道查 `componentId` | 每次 apply **立刻**跑现有校验，issues 写进工具返回值（缺插件也写在这里）。预览可以仍展示图，但不靠模型自觉保证正确 |

校验不要理解成「最后生成完再检查一次就行」。Cursor 也不是等全部写完才看编译错误：每改一次就把诊断塞回对话。模型下一轮看见 issues，才可能再改。没有这份返回值，它就没有「看报错」这一步。

工具说明也不必写成操作手册长文。够用的是：**有哪些按钮、参数形状、何时用、返回里会有什么**。真正教它「这次改错了」的是校验结果，不是再写一段「请按先读后改的顺序」。

---

---

---

---

## 工具箱（Cursor 文件工具的 BPMN 对应）

| Cursor / Claude | kiwi |
|-----------------|------|
| 打开的文件 / Read | 每轮附件：当前 `workspace` BPMN（截断）+ 选中元素；需要细节可再 `read_workspace` |
| StrReplace / ApplyPatch | **`apply_edit_plan`**：参数为 EditPlan IR，确定性改 XML（现有 [`EditPlanApplicator`](kiwi-bpmn-designer-agent/src/main/java/com/kiwi/bpmn/designer/agent/apply/EditPlanApplicator.java)） |
| 诊断 / 测试输出 | 该工具返回值内附 **validate issues**（缺插件也写在这里，不另开安装闸门） |
| Grep / codebase search | 现有 MCP 发现：[`DesignerAgentToolScope.DiscoveryToolNames`](kiwi-bpmn-designer-agent/src/main/java/com/kiwi/bpmn/designer/agent/mcp/DesignerAgentToolScope.java) |
| 写仓库 / 部署 | **不开放** `WriteToolNames`（save/deploy/install）给模型 |

循环实现：Spring AI `ChatClient` 注册上述工具，走 **内部 tool-calling loop**（已有 [`DesignerAgentToolTraceContext`](kiwi-bpmn-designer-agent/src/main/java/com/kiwi/bpmn/designer/agent/mcp/DesignerAgentToolTraceContext.java) 推 SSE）。上限 `maxToolRounds`（如 8），防止空转。

主路径 **禁止**：`PlanGenerator.buildPrompt()` 单 user 字符串，再由宿主解析 JSON 后强制 apply。模型若只在文本里贴 EditPlan 而不调工具，本轮视为没改图（可在 system 里强调必须调工具）。

---

## 预览（kiwi 特有，对应「未保存缓冲 / 可撤销的编辑」）

Cursor 改的是磁盘文件，用户用 Undo / Reject。kiwi 改的是设计器画布：

- 工具成功改图 → `candidateXml` 推到画布（预览），`gate.type=preview`，只留「拒绝并回退」。
- 用户继续打字 → 把当前画布写入 `workspace`（保存缓冲）再新一轮 harness。
- 点回退 → 丢掉 candidate，画布回到预览前 `workspace`，stage=idle。不上第二次 LLM。

没有计划批准。没有安装打断。

---

## Session / API

```text
DesignerAgentSession
  messages[]
  workspace.bpmnXml, selectedElementId
  candidateXml | null
  stage: idle | running | preview | error
  ui.graphRunning, ui.inputEnabled    // 字段名可沿用；running 时 busy
```

- `POST /sessions/turns`：唯一发话（body：`message` + `canvasBpmnXml`）。
- `POST /runs/{id}/gates`：仅 `preview_reject`。
- GET session：`messages` + `ui` + 可选 `candidateXml`。前端 `busy = ui.graphRunning`，不信 `active`。
- 处理中 409。崩溃以 Session 为准，不要 Graph checkpoint。

废弃：GraphFactory / Runtime / `human_*` / 分类器 / `confirm_plan` / follow-up / clarify / install actions。

澄清选项 UI：本轮不做（问句走聊天）。若以后要做选择题，也是消息里的交互，不是 Graph 节点。

---

## 丢掉 / 保留

**保留：** EditPlan IR、Applicator、校验器、MCP 发现、一流程一会话、预览回退按钮。

**丢掉：** StateGraph、全部 human 节点、计划/安装/澄清/追问闸门、`userScenario` 任务单主路径、GateIntentClassifier。

---

## 实施顺序

1. Session 为唯一真相；GET 带 messages / ui / preview 缓冲。
2. ChatClient：`.messages(近 N 轮)` + 工作区附件 + 工具循环；契约测试（第二轮含第一轮原文；改图必现 `apply_edit_plan` 工具调用）。
3. 注册 `apply_edit_plan`（内含 validate 返回）；删除宿主 generate→apply→validate 边。
4. `POST /sessions/turns`；gates 仅回退。
5. 前端 `send` 只走 turn；去掉计划/安装/澄清闸门；busy 只跟 running。
6. 删 Graph 与分类器；README 改成 harness 描述。

---

## 明确不做什么

- 不上 OpenAI Assistants 服务端记会话。
- 不让模型直接输出 BPMN XML。
- 不把「线性 TurnPipeline」当成对齐：那仍是宿主状态机，只是比 Graph 短。
- 本轮不做 Cursor 式 subagent / 远轮自动摘要（截断近 N 轮即可）。

---

## 成功标准

1. 第二轮模型输入含第一轮 user/assistant 原文。
2. 改图路径上有工具调用，而不是宿主在 LLM 返回后偷偷 apply。
3. 预览打字不会回退；回退只有按钮。
4. 刷新后气泡与模型输入同源。
5. 代码无 `interruptBefore(human_*)`；无 `await_plan` / `await_install` / `await_follow_up`。
