# Implementation notes

## 2026-08-03 首轮落地

- 配置：`kiwi.ai.workflow-authoring.*`（默认 `enabled=false`）
- Catalog / 抽词 / Validator / PlanGenerate（LLM 可选，失败回退最小 BPMN）
- 内部流程：`classpath:bpm/ai/kiwi_ai_workflow_authoring.bpmn`，启用时 ApplicationReady 部署
- 桥接 API：`/ai/workflow-authoring/**`
- 助手分流：设计器会话 + 场景意图正则 → 启动编排；候选 XML 以 `bpmnXml` + `previewOnly=true` 预览
- 人机：User Task（Preview / Install / Ask）；完成走 `completeTask`
- 前端：`previewOnly` 走 `importBpmnXml` 不自动保存；`AiWorkflowAuthoringService` 已加

## 与 design 差异

- 安装确认后「真正调用 installPlugin」仍为占位：User Task 接受后回到 Validate（需用户先装好或后续接 install Delegate）
- Catalog installable 主要来自远程市场 plugin 列表（启用时）
- 前端尚未做完整阶段卡片 UI（可用 API + 助手文本）
