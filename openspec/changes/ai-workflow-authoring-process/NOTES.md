# Implementation notes

## 2026-08-03 首轮落地

- 配置：`kiwi.ai.workflow-authoring.*`（默认 `enabled=false`）
- Catalog / 抽词 / Validator / PlanGenerate（LLM 可选，失败回退最小 BPMN）
- 内部流程：`classpath:bpm/ai/kiwi_ai_workflow_authoring.bpmn`，启用时 ApplicationReady 部署
- 桥接 API：`/ai/workflow-authoring/**`
- 助手分流：设计器会话 + 场景意图正则 → 启动编排；候选 XML 以 `bpmnXml` + `previewOnly=true` 预览
- 人机：User Task（Preview / Install / Ask）；完成走 `completeTask`
- 前端：`previewOnly` 走 `importBpmnXml` 不自动保存；`AiWorkflowAuthoringService` 已加

## 2026-08-04 前端阶段面板

- `bpm-ai-chat` 接入 `AiWorkflowAuthoringService`：按目标流程 `statusByTarget`，聊天回合后刷新
- 右上角「AI 写工作流」面板：阶段标签 + 预览确认/拒绝、安装确认/拒绝、追问提交
- `await_preview` 时自动 `importBpmnXml`（不落库）；确认保存后后端 SaveDelegate 落库，前端再同步画布
- `ChatComponent` 增加 `turnCompleted` 输出

## 与 design 差异

- 安装确认后「真正调用 installPlugin」仍为占位：User Task 接受后回到 Validate（需用户先装好或后续接 install Delegate）
- Catalog installable 主要来自远程市场 plugin 列表（启用时）
