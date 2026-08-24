# AI 改现有图 + 回写画布

> **状态：已完成，路径已迁移。**  
> 原 `workflow-authoring` / `ClientAction.bpmnXml` 方案已废弃。现行实现见 [bpm_designer_agent_greenfield_13dbd3a0.plan.md](./bpm_designer_agent_greenfield_13dbd3a0.plan.md)。

## 现行做法（Designer Agent）

| 目标 | 实现 |
|------|------|
| 在现有图上改，而非只从零生成 | ingest 带当前画布 XML；`EditPlan` 增量 patch |
| 对话结束回写画布 | SSE `preview_ready` → 前端 `importBpmnXml`（预览） |
| 是否落盘 | 用户 `confirm-preview` 后再 save；不自动 save |

## 历史方案（勿再实现）

- start 把画布 XML 写入 `candidateXml`；`kiwi.ai.workflow-authoring.auto-save-canvas`
- 助手响应 `ClientAction.bpmnXml`（`previewOnly = !autoSave`）
