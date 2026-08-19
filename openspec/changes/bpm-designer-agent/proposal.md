# Change: BPM 设计器 Agent（Greenfield）

## Why

旧 write-workflow / assistant_designer_* 路径黑盒、双轨并存，无法提供 Cursor 式思考过程与 Plan 审阅。需要独立 Agent 子系统。

## What

- 新模块 `kiwi-bpmn-designer-agent`：EditPlan IR、PatchApplier、Orchestrator
- 新 API `/bpm/designer-agent/**` + SSE
- 新前端 `bpm-designer-agent` 面板（替换设计器内 bpm-ai-chat 改图入口）
- 配置 `kiwi.bpm.designer-agent.enabled`

## Impact

- 废弃（设计器改图）：write-workflow、assistant_designer_* ClientAction
- 不动：全局 `/ai/assistant`、app-chat
