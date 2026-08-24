## Context

当前设计器 AI（`bpm-ai-chat` → `POST /ai/assistant`）将已装组件前 60 条与截断 BPMN XML 注入 system 消息，模型经 `assistant_designer_bpmn_xml` 登记整图替换并由前端自动保存。校验仅 XML well-formed；模板/可装插件未进入主路径；toolbar 工具与 prompt 不一致。

本设计用 **Kiwi 内部 BPMN 流程**编排「AI 写工作流」管线，Java Delegate 承载检索、LLM 调用与校验；设计器通过启动流程实例与完成 User Task 与人机停顿交互。

## Goals / Non-Goals

**Goals:**

- 场景驱动：抽词 → Catalog（已装 + 可装 + 模板摘要）注入 → Plan/生成 → 多层校验闭环。
- 用 process key `kiwi_ai_workflow_authoring`（可配置）表达控制流与人机任务。
- 坏图不落盘；缺插件与预览均需用户确认。
- Catalog 与校验共用同一 componentId 空间。

**Non-Goals:**

- Dify / LangGraph / 外挂 Agent 工作流平台。
- v1 向量 RAG；Operaton deploy dry-run 进闭环。
- 未确认自动安装市场插件或自动 `PUT` 目标流程。
- 用开放 ReAct「模型自由选下一步」作为主控。

## Decisions

### D1：用 Kiwi BPMN 编排管线（非纯 Java 状态机、非 Dify）

- **选择**：部署内部流程定义，Service Task + User Task + 排他网关 + 修复回边。
- **相对纯 Java 编排器**：人机停顿、可观测（流程实例/变量/历史）、与产品定位一致（dogfood）。
- **相对 Dify**：无双运行时；校验与安装走现有 API。
- **隔离**：元流程实例变量与用户正在编辑的目标 `processId` 分离；禁止元流程与目标流程互相覆盖定义文件。

### D2：Catalog 先查后注入为主，MCP 为辅

- 编排器/Delegate 抽 `keywords`/`tags` 后查组件与 `bpmMarket_aiPage`、远程可装差集，写入流程变量 `catalogJson`。
- LLM Plan/生成节点 **只**在 Catalog 内选型；`available_to_install` 必须标 `requiresInstall`。
- MCP 工具保留给通用助手或「再找找」补索，不作为场景生图唯一发现机制。

### D3：抽词非 RAG

- v1：轻量 LLM 输出结构化 keywords/tags，或规则+同义词；再走现有 keyword/正则查询。
- 不上 embedding 索引；召回不足时再演进混合检索。

### D4：校验与 Issue 分派在代码，网关只看聚合结果

- `BpmAiWorkflowValidator` 并行收集 Issue（L0 语法、L1 结构、L2 componentId/缺插件/必填参数）。
- 流程变量如 `dispatchCode`：`PASS` | `REPAIR` | `INSTALL` | `ASK`；网关路由，避免为每个 issue code 画一条边。
- `repairRound` 默认上限 3（配置项）。

### D5：设计器桥接

- 入口：assistant 检测场景/改图意图 → 启动（或 correlate）`kiwi_ai_workflow_authoring`，传入 `scenario`、`targetProcessId`、`selectedElementId`。
- 预览：流程到达 Preview User Task 前将通过校验的 XML 经 ClientAction 或轮询任务载荷交给前端 **import 预览**；完成任务时带 `confirmed=true|false`。
- 安装确认：User Task 载荷含 `pluginHint`；确认后 Service Task 调现有安装 API，再回校验。

### D6：与现有 `assistant_designer_bpmn_xml` 关系

- 生图主路径由元流程登记「待预览 XML」，不再默认自动 save。
- 简单「追加单组件」等短路径可暂时保留 match_component；整图场景走元流程。
- 收紧：未校验通过的 XML 不得直接落盘。

### D7：人机停顿暂定 User Task（备选：会话确认）

- **本期选择**：缺插件确认、追问、预览确认均用 **User Task**；设计器 Chat 桥接查询与 `complete`。
- **理由**：停顿由引擎记账，可恢复、可观测，与 dogfood Kiwi 流程一致。
- **备选（本期不做）**：不用 User Task——Service Task 推 Chat（ClientAction/会话），用户在对话中确认后经消息/回调写回变量再继续。更贴 Chat、实现快，但状态在会话侧，长停顿与审计弱。
- **切换成本**：主要改三个停顿节点与桥接层；Catalog/Validate/Save 可复用。允许日后混合（预览/装插件保留 UT，轻量追问改会话）。

## Risks / Trade-offs

- **[Risk] 元流程与目标流程混淆** → 固定内部 key/分类；变量命名 `targetProcessId`；部署包与业务项目隔离。
- **[Risk] User Task 与 Chat UI 不同步** → 明确任务查询 API 与完成契约；chat 展示当前阶段。
- **[Risk] 抽词召回差** → 兜底注入「已装热门/最近使用」；允许用户补充关键词后重跑 Catalog。
- **[Risk] LLM 费用与时延** → 抽词短输出；Catalog Top-N 截断；修复轮上限。
- **[Trade-off] Dogfood 增加建模与引擎依赖** → 换取可观测与人机停顿一致性；纯 Java 状态机作备选但本期不采用。

## Migration Plan

1. 实现 Catalog / Validator / Delegate（可先单元测试）。
2. 建模并部署 `kiwi_ai_workflow_authoring`（dev 环境）。
3. 设计器桥接 feature flag（如 `kiwi.ai.workflow-authoring.enabled`）；默认关或仅场景生图入口开启。
4. 验证通过后，场景生图切到元流程；旧单轮 XML 路径降级为兼容。
5. 回滚：关闭 flag，恢复现有 assistant 行为。

## Open Questions

- Preview/Install/Ask User Task 是用引擎 Tasklist API 还是专用 REST + 设计器侧完成（倾向专用桥接以贴合 Chat UX）。（人机形态已暂定 User Task，见 D7。）
- 元流程 BPMN 是打进 backend classpath 资源还是经管理端部署（倾向 classpath 种子 + 启动校验存在）。
