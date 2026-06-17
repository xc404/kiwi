# 归档说明（spec 过时，不应作为 main 真相）

**日期：** 2026-06-17

本 change 的 BPM 设计器 AI 能力**已在代码中落地**，但 proposal / design / tasks / delta spec 与当前实现不一致；**不得**将本目录 delta spec 当作 `openspec/specs/` 的权威来源。

## 实现路径（以代码为准）

初稿描述专用 `POST /ai/bpm-designer` 与 `BpmDesignerAssistantService`；**实际**为：

- 统一 `POST /ai/assistant` + `conversationScope="bpm-designer"`
- 前端 `bpm-ai-chat` 经 `messagesEnricher` 注入 BPMN / 组件库上下文
- 后端 `AssistantDesignerTools`（`assistant_designer_bpmn_xml`、`assistant_designer_match_component`）登记 `ClientAction`
- 前端 `createBpmDesignerAssistantHandlers` 经 `ChatComponent.actionHandlers` 执行

## 为何 delta spec 过时

1. **工具栏等价操作**：spec 要求经 AI 执行全套 `BpmToolbar` 命令；`assistant_designer_toolbar` 在 `AssistantDesignerTools` 中**已注释**，改图类意图引导走 `bpmn_xml`。
2. **架构契约**：无 `BpmDesignerAction` DTO、无 `/ai/bpm-designer`；与 `admin-ai-client-actions` 共用 `ClientAction` + 编排层。
3. **规格质量**：delta requirements 无 Scenario，OpenSpec 校验未通过；归档时误用 `--no-validate` 曾短暂写入 main spec。

## Main spec 处理（2026-06-17 撤回）

曾同步至 `openspec/specs/bpm-editor-ai-assistant/spec.md`，已**删除**。若需正式能力文档，应新开 change 按当前 `ClientAction` / `AssistantDesignerTools` / 前端 handlers 重写。
