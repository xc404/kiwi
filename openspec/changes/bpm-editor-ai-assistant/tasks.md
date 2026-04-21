# Tasks

## 1. 契约与后端

- [x] 定义 `BpmDesignerAction`（及请求/响应 DTO）与 `POST /ai/bpm-designer`（路径以评审为准）；`@SaCheckLogin` 与流程资源权限校验。
- [x] 实现 `BpmDesignerAssistantService`：专用 `ChatClient` 或共享 bean + 独立 System prompt；注册 Tool：`listComponents`（或复用现有组件列表 API 内部调用）、`proposeAppendComponent`、`proposeToolbar`、`proposeBpmnXml` 等，将结果写入专用 `ActionContext`（模式对齐 `MenuAssistantActionContext`）。
- [x] BPMN XML 校验工具（长度、解析、可选 camunda/kiwi 扩展约束按项目现状最小集）。

## 2. 前端嵌入与桥接

- [x] 在 `bpm-editor`（模板 + 样式）中嵌入 AI 对话 UI；未启用 AI 时隐藏或禁用入口。
- [x] 新建桥接服务/类：调用新接口，将响应 `actions` 转为：
  - `toolbar` → `editorActions.trigger` 或 `BpmEditorToken` 方法；
  - `bpmnXml` → `bpmnModeler.importXML` + 用户提示；
  - `appendComponent` → 解析 `ComponentDescription` 后复用 `appendComponentFromContextPad` 或等价逻辑。
- [x] 错误与加载状态：与 `AiChatService` 模式一致（`showLoading: false` 可选）。

## 3. 联调与文档

- [ ] 手工验收：至少覆盖 undo/save/deploy、导入小改 XML、追加一个已知组件。
- [x] 在 `openspec/changes/bpm-editor-ai-assistant/specs/` 中保持 spec 与实现一致（如有偏差更新 spec）。
