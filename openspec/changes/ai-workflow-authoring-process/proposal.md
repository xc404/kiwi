## Why

Kiwi 设计器内「AI 写工作流」仍是单轮 LLM 整图 XML 替换：组件上下文仅前端截断的 `id|name`，校验几乎只有 well-formed，缺插件无法引导，且常自动保存。需要把「场景 → 检索上下文 → 设计 → 校验闭环 → 人确认落盘」做成可预期产品能力，并用 **Kiwi 自身工作流**编排该管线（dogfood），而不是外挂 Dify 或开放 ReAct。

## What Changes

- 新增平台内部流程（建议 process key：`kiwi_ai_workflow_authoring`），用 Service Task / User Task / 网关实现：抽词 → Catalog 检索注入 → LLM Plan/生成 → 多层校验 → 修复回边 → 缺插件确认 → 预览确认 → 保存。
- 新增 Catalog 组装：按场景 keywords/tags 检索已装组件、市场模板摘要、可装但未装插件/组件差集；**不**以向量 RAG 为主路径。
- 新增 BPMN 多层校验与 Issue 分派（语法、结构、componentId、缺插件、必填参数）；校验失败不写入目标流程。
- 设计器 / `POST /ai/assistant` 改为启动或驱动该内部流程；`bpmnXml` 以预览为主，用户确认后再保存。
- MCP（`bpmComp_*` / `bpmMarket_*`）保留为按需加深；场景生图主路径以服务端注入 Catalog 为准。
- **非目标**：Dify/外挂 Agent 平台、v1 向量 RAG、未确认自动装插件、Operaton deploy dry-run 进闭环。

## Capabilities

### New Capabilities

- `ai-workflow-authoring`: 场景驱动的 AI 流程编写管线（Kiwi 内部流程编排、Catalog 注入、校验闭环、人机确认、与设计器桥接）

### Modified Capabilities

- （无）归档过时的 `bpm-editor-ai-assistant` 未进入 main specs；本 change 以新能力为准，不修改现有 main spec 需求条文。

## Impact

- 后端：`com.kiwi.project.ai`（编排入口、抽词、Catalog、Validator、Delegate）、BPM 流程定义部署、与 `BpmComponentService` / 模板市场 / 远程市场安装 API 集成。
- 前端：`bpm-ai-chat`、assistant action handlers（预览确认、缺插件确认）、流程用户任务桥接。
- 运行时：新增内部流程实例与变量契约；与用户正在编辑的目标 `processId` 隔离。
- 依赖：现有 Spring AI `ChatClient` / MCP；不新增 Dify 或向量库依赖。
