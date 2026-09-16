# 设计器 Agent 重新设计：对齐 Cursor / Claude

**状态：第一版已落地（2026-09-15）**  
**对齐对象：** Cursor Agent / Claude Code — **对话线程 + 工具循环（harness）**，不是 StateGraph，也不是「generate→apply→validate」宿主状态机。  
**Git 基线：** `feat/designer-agent-harness` 从干净 `origin/master`（`9b46886`）拉出。  
**实现方式：绿场。** 不 merge、不搬 `feat/extract-kiwi-bpmn-assistant` 里的 Graph / Session / 闸门代码。旧实验留在那条分支（最新提交 `4917ddb`，未 push）。  
**范围：** 代码在 `kiwi-admin`（未新建 Maven 模块）。工具包名 `com.kiwi.bpmn.designer.harness`（故意不在 `com.kiwi.project`，避免扫进全局 MCP）。HTTP：`/bpm/designer-harness`。

**第一版限制：** `apply_bpmn_ops` 经 `AiWorkflowPlanCompiler` 重排 DI；含泳道/子流程/边界事件的图会拒绝保存。契约测试（第二轮 messages 含第一轮原文）尚未写。

**2026-09-15 实测补丁：**

1. **布局：** 编译器不再按 `nodes` 数组下标从左到右排。`AiWorkflowPlanLayout` 按 sequenceFlow 分层（列 = 最长路径）。前端虽有 `bpmn-auto-layout`，但它用裸 `bpmn-moddle` 重写 XML，会丢掉 `kiwi`/`camunda` 扩展，不能拿来排已绑定组件的图。
2. **`kiwi:componentId`：** 写出与前端 `kiwi.json` 的 uri 统一为 `KiwiBpmnXml.Namespace`（`http://kiwi.io/schema/bpmn`）。同时写 `camunda:property name="componentId"`。

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
  messages[]          // 唯一聊天真相
  workspace.bpmnXml   // 当前已保存的图 = 流程定义里的 BPMN（Cursor 已写入磁盘的文件）

用户 Enter
  → append user
  → 若画布有手改：先写入 workspace 并保存流程定义
  → running=true
  → loop:
        ChatClient(messages + tools)
        若 tool_calls → 执行；改图成功则立刻 workspace←新XML 并保存流程定义，推画布
        否则 → append assistant 文本，停
  → idle
  → running=false
```

原则：

1. **模型决定下一步**（说话 / 查组件 / 改图 / 再改），宿主不预先路由 `explain | clarify | apply | ask`。
2. **改图必须走工具**，等价 Cursor 的 edit；禁止让模型直接吐 BPMN XML。
3. **宿主不在循环外再跑一遍 apply/validate 状态机。** 校验是工具返回值。
4. **没有** `await_plan` / `await_install` / `await_follow_up` / `await_ask` / `await_clarify` / **`await_preview`**。问用户 = 聊天里问。
5. **没有预览缓冲、没有 candidateXml、没有拒绝回退闸门。** 工具每成功改一次就保存（workspace + 流程定义 + 画布），和 Cursor 每次 StrReplace 写盘一样。不满意就再说一句让它改回去（本轮不做 Undo 栈）。

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
6. 这时循环结束，图画的就是已保存版本；你再打字就是下一轮。没有「先预览再确认」。

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
| kiwi 落点 | 改图工具参数必须是 EditPlan schema；写清「改图只能调这个工具，禁止输出 XML」；MCP 让它查 `componentId` | 每次 apply **立刻**校验，issues 写进工具返回值（缺插件也写这里），并 **立刻保存** 流程定义。不靠模型自觉，也不另开预览闸门 |

校验不要理解成「最后生成完再检查一次就行」。Cursor 也不是等全部写完才看编译错误：每改一次就把诊断塞回对话。模型下一轮看见 issues，才可能再改。没有这份返回值，它就没有「看报错」这一步。

工具说明也不必写成操作手册长文。够用的是：**有哪些按钮、参数形状、何时用、返回里会有什么**。真正教它「这次改错了」的是校验结果，不是再写一段「请按先读后改的顺序」。

### EditPlan 在 harness 里还有多大用？

拆成两件事，益处差很多：

| 旧含义 | harness 里还要不要 |
|--------|-------------------|
| 给人看的「变更计划」：先出步骤、等人批准再改图 | **不要。** 闸门已取消。用户看画布，不看 IR。 |
| 给程序用的**改图补丁语言**：addNode / addFlow / …，由代码改 XML | **要，而且是对齐 Cursor 时最不该扔掉的一层。** |

Cursor 的「文件」是 `.ts` / `.java`，模型用 StrReplace 改几行，编译器/linter 再报错。kiwi 的「文件」是 BPMN XML：命名空间、DI 坐标、`bpmnElement` 对不上就会整图坏掉。让模型直接吐或补丁 XML，等于让它当 bpmn-moddle。EditPlan 的益处是：

1. **模型不碰 XML。** 坐标、连线补齐、标签合法性由 Applicator 做。
2. **工具参数有 schema。** 比「把整份 XML 当参数」稳，也比自由文本好校验。
3. **每次调用都能跑 BPMN 校验。** 返回 issues，才有「看报错再改」。
4. **`componentId` + 参数** 能对上组件库，而不是模型瞎编 `camunda:class`。

它**不再**提供：给人审批、当对话摘要、当 Graph 的 `await_plan`。助手气泡用自然语言说「加了删除文件节点」即可，不要把 EditPlan JSON 摊在 UI 上。

实现上仍叫 EditPlan 可以（内部 IR），对外工具名更宜叫 `apply_bpmn_ops` / 拆成 `add_node`、`connect` 等小按钮——那只是一次调一批还是一次调一条，**还是同一套 IR**，不是回到「模型写 XML」。不要为了「更像 Cursor」改成 Write 整份 `.bpmn`。

---

## 第一版要实现的 tools（仅这些）

模型只能调下面 **3 个**。校验、保存流程定义、刷新画布都不是独立工具，而是 `apply_bpmn_ops` 成功后的宿主行为。当前 BPMN 每轮当附件塞进 prompt，**不**做 `read_workspace`。

| 工具名 | 作用 | 参数要点 | 返回 |
|--------|------|----------|------|
| `search_components` | 按关键词查组件库（Cursor 的 Grep） | `query`，可选 page | 少量条目：id、name、group、一句话描述。**不要**一次倒出全库。现有 `bpmComp_aiPage` 没有关键词，不能直接当这个工具。 |
| `get_component` | 取一个组件的参数契约 | `componentId` | 参数 key、类型、是否必填、说明。addNode 前要能拿到这个。master 没有 `bpmComp_get`，绿场自己包 DAO。 |
| `apply_bpmn_ops` | 唯一改图入口（Cursor 的 StrReplace） | EditPlan：`operations[]`，op ∈ `addNode` / `removeNode` / `updateNode` / `addFlow` / `removeFlow` / `setProcessMeta` | 成功：已保存；附 `issues`（含缺插件）。失败：错误信息，不写盘。 |

同一轮里可以多次 `search` → `get` → `apply` → 再 `apply`。`maxToolRounds` 默认 8。

**不要做成工具（第一版）：**

- `read_workspace` / 读整份 XML（已是附件）
- 单独的 `validate_bpmn`（跟在 apply 返回里）
- `bpmPd_save` / `deploy` / `start`（apply 内部 persist）
- 市场、远程插件、`installPlugin`、环境列表
- 拆成 6 个 add_node/connect…（以后可拆，第一版一个入口、schema 更短）

工具说明必须写清：改图只能 `apply_bpmn_ops`；禁止输出 BPMN XML；serviceTask 必须先 `get_component` 再填 `componentId` 与 parameters。

循环：ChatClient 注册这 3 个，内部 tool-calling，上限 8 轮。模型只在文本里贴 ops 而不调工具 → 本轮当没改图。

---

---

## 保存（无 preview）

只维护一份图：`workspace.bpmnXml` = 流程定义已保存 XML = 画布正在看的内容。

- 改图工具成功 → 立刻 persist 流程定义，SSE/`xml` 事件让前端 `import` 画布。同一轮里改多次就保存多次。
- 用户发下一句前若手改过画布：turn 带上 `canvasBpmnXml`，先保存再跑模型。
- **不设** candidateXml、preview gate、「拒绝并回退」。本轮不做 Undo 栈；要还原就口头再改。

没有计划批准。没有安装打断。

---

## Session / API

```text
DesignerAgentSession
  messages[]
  workspace.bpmnXml, selectedElementId
  stage: idle | running | error
  ui.running, ui.inputEnabled
```

- `POST /sessions/turns`：唯一发话（`message` + `canvasBpmnXml`）。**无 gates API。**
- GET session：`messages` + `ui` + 当前 `bpmnXml`（给刷新后灌画布）。
- 前端 `busy = ui.running`。处理中 409。崩溃以 Session 为准。

废弃：Graph、分类器、confirm_plan / preview / follow-up / clarify / install。

澄清选项 UI：本轮不做。

---

## 丢掉 / 保留

**绿场要写的：** Session + turns API、ChatClient 工具循环、EditPlan IR、确定性改 XML、校验进工具返回、MCP 发现、一流程一会话、**改完即保存**。

**不要从 extract 分支带过来：** StateGraph、human_*、计划/安装/澄清闸门、`userScenario` 任务单、GateIntentClassifier。

可对照 extract 里 Applicator / 校验的**算法**，在新模块里重写。

---

## 实施顺序

绿场落在 `feat/designer-agent-harness`（相对 master 几乎只有本计划文件）。

1. 新建模块（名称可仍用 `kiwi-bpmn-designer-agent`）+ 接入 admin：Session、`POST /sessions/turns`、SSE。
2. ChatClient：`.messages(近 N 轮)` + 工作区附件 + 工具循环；契约测试（第二轮含第一轮原文；改图必现 `apply_edit_plan` 工具调用）。
3. 三个工具：`search_components`、`get_component`、`apply_bpmn_ops`（成功即保存）。
4. 前端侧栏：`send` 只走 turn；工具改图后刷新已保存画布；busy=running。无预览条、无回退按钮。
5. README。

旧分支 `feat/extract-kiwi-bpmn-assistant` 仅作对照，不作为本分支 merge 源。

---

## 明确不做什么

- 不上 OpenAI Assistants 服务端记会话。
- 不让模型直接输出 BPMN XML。
- 不把「线性 TurnPipeline」当成对齐：那仍是宿主状态机，只是比 Graph 短。
- 本轮不做 preview 闸门 / Undo 栈 / 远轮自动摘要。

---

## 成功标准

1. 第二轮模型输入含第一轮 user/assistant 原文。
2. 改图路径上有工具调用，而不是宿主在 LLM 返回后偷偷 apply。
3. 改图工具成功后流程定义已是新 XML；刷新设计器看到的是保存结果，不是预览稿。
4. 刷新后气泡与模型输入同源。
5. 无 `await_preview` / `candidateXml` / gates。
