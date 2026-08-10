# AI authoring：改现有图 + 回写画布

## 目标

1. Generate 支持在现有 BPMN 上修改，而非只从零生成
2. 每轮对话结束把 candidateXml 经 `bpmnXml` action 回前端更新画布；是否自动保存由配置决定

## 方案

- start 传入当前画布 XML（从设计器 system 上下文解析），写入 `candidateXml` 种子
- PlanGenerate 提示词区分「新建 / 在上一版上修改」；有 previousXml 时失败回退保留原图
- 配置 `kiwi.ai.workflow-authoring.auto-save-canvas`（默认 true）；前端可在 system 中写 `aiAuthoringAutoSave: true|false` 覆盖
- 助手响应带 `ClientAction.bpmnXml`（previewOnly = !autoSave）
