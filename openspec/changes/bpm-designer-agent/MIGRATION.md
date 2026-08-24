# 从 write-workflow 迁移到 designer-agent

设计器改图使用 `/bpm/designer-agent/**`。全局 `POST /ai/assistant` 不再改画布。

## 新入口

| 用途 | 路径 |
|------|------|
| 启动 run | `POST /bpm/designer-agent/runs` |
| SSE 事件 | `GET /bpm/designer-agent/runs/{id}/events` |
| 确认 Plan / 预览 / 追问 | `POST /bpm/designer-agent/runs/{id}/confirm-*` 等 |
| 前端 | 设计器右侧 `bpm-designer-agent` 面板 |
| 开关 | `kiwi.bpm.designer-agent.enabled`（`KIWI_BPM_DESIGNER_AGENT_ENABLED`） |

## 已删除

- `WriteWorkflowCtl`、`WriteWorkflowOrchestrator`、`WriteWorkflowSessionService`、`AssistantIntentService`
- `WriteWorkflowSession` / `WriteWorkflowStatus` / `WriteWorkflowIntent`
- `AiAssistantService.tryWriteWorkflow`
- `AssistantDesignerTools`（`assistant_designer_*`）
- `assistant.delegate.*` 元流程 JavaDelegate 壳
- `AssistantCatalogContextBuilder`
- `AssistantExecutionUtils`
- 评测 IT `AssistantCreateOrderApiIT`（已由 designer-agent SSE 评测替代）

## 仍复用

`kiwi-bpmn-assistant` 的 `AssistantPlanCompiler`、`AssistantWorkflowValidator`、SPI 适配器。
全局助手仍保留 `assistant_navigate`。
