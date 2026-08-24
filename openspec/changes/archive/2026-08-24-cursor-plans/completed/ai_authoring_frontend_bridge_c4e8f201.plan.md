# AI 写流程前端桥接

## 目标

把 `AiWorkflowAuthoringService` 接到设计器 `bpm-ai-chat`，展示编排阶段并完成 Preview / Install / Ask User Task。

## 方案

1. `ChatComponent` 增加 `turnCompleted` 输出：助手成功回复后通知宿主刷新编排状态。
2. `bpm-ai-chat` 增加编排状态面板：
   - 按 `targetProcessId` 调用 `statusByTarget`
   - 助手回合后、processId 变化时刷新
   - 有 `candidateXml` 且 `await_preview` 时 `importBpmnXml`（不保存）
   - 按钮：确认保存 / 拒绝重生成；确认安装 / 拒绝；提交追问答案
3. `completeTask` 后按新 status 再刷画布；确认保存后用候选 XML `importBpmnXml`（后端已落库）。

## 不改

- 通用 Chat 业务逻辑尽量少动
- 不实现真正 installPlugin（后端仍占位）
