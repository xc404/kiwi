## Context

- 现有 **`/ai/chat`**（纯文本）与 **`/ai/assistant`**（`AiAssistantResponse`：`content` + `actions`，基于 `ChatClient` + `MenuAssistantActionContext` + `MenuAssistantTools`）已建立 Spring AI 助手范式。
- **`BpmToolbar`** 通过 `editorActions` 触发 undo/redo/copy/paste/zoom/find 等，并调用 `BpmEditorToken` 的 `save` / `deploy` / `start` / `saveAsComponent`；导出走 `saveXML` / `saveSVG`。
- **`BpmEditor`** 已持有 `BpmnModeler`、`ComponentProvider` / `ComponentService`，追加组件路径为 `appendComponentFromContextPad`（与左侧组件面板一致）。

## Goals

1. **工具栏代理**：将自然语言映射为受控动作集合（白名单），与 `BpmToolbar` 行为一致，避免任意代码执行。
2. **BPMN 修改**：优先 **增量** 策略——模型通过工具返回「完整替换 XML」或「在服务端验证后的片段合并」由产品权衡；首版推荐 **前端执行 `importXML`** 前由后端做基础校验（well-formed、根元素、尺寸上限）。
3. **智能加组件**：后端工具可读组件目录（与前端 `ComponentProvider` 同源数据接口），返回结构化 `appendComponent`（`componentId` 或 `key`、`relativeToElementId` 可选）；前端负责调用现有 `initElement` + `autoPlace`。

## API 形状（建议）

- **`POST /ai/bpm-designer`**（名称以实现为准）请求体扩展自现有 `ChatRequest`：
  - `messages`：同现网。
  - `processId`：当前流程 id（权限与审计）。
  - `bpmnXml` 或 `bpmnXmlHash`：可选；若过大则只传 hash + 服务端缓存会话（实现阶段再定）。
- **响应**：`{ content: string, actions: BpmDesignerAction[] }`，其中 `BpmDesignerAction` 为判别联合，例如：
  - `type: "toolbar"`，`command: "undo" | "redo" | ...`（与 `editorActions` / `BpmToolbar` 对齐表）。
  - `type: "bpmnXml"`，`xml: string`（UTF-8），经后端校验。
  - `type: "appendComponent"`，`componentKey: string`，`sourceElementId?: string`。
  - `type: "navigate"`：复用现有 `ClientAction`（若需跳转管理页）。

与 **`/ai/assistant`** 隔离，避免菜单工具与 BPM 工具混在同一 prompt，减少误触发。

## 前端集成

- 在 `bpm-editor` 侧栏或底部抽屉嵌入 `ChatComponent` 变体（`embed` 或新 `mode="bpm-designer"`），注入：
  - `processId`、`getBpmnXml(): Promise<string>`（`saveXML`）、可选选中元素 id。
- 收到 `actions` 后 **顺序执行**，每一步失败则中断并 `NzMessage` 提示；危险操作（全量 XML 替换）可要求二次确认（实现阶段定）。

## 安全与治理

- 服务端校验 BPMN XML 大小与基本结构；仅登录用户且 `processId` 与路由一致时允许写操作类工具。
- 工具层不直接执行数据库写流程定义；**保存**仍走用户显式保存或明确动作 `save`，避免模型隐式持久化。

## 测试建议

- 后端：Tool 注册与 JSON 反序列化单测；XML 校验单测。
- 前端：桥接层单元测试（mock `BpmnModeler`）验证 action → 调用映射。
