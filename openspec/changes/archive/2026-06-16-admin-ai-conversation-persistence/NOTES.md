# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/ai_会话_mongo_持久化_ef4d4edf.plan.md`，已在此前归档至本目录；plan 文件已删除，以本 OpenSpec 归档与代码为准。

## 实现状态

- **已落地**：`AiChatConversation` / `AiConversationService` / `AiConversationCtl`、前端 `AiConversationService` + `ChatComponent` 会话 UI、BPM `scope=bpm-designer` + `scopeRef=processId`
- **tasks.md** 已全部勾选

## 与 plan 的差异（以代码为准）

- 配置项命名：plan 草案为 `kiwi.ai.conversation.max-messages`；实现见 `AiChatProperties`（`conversation-max-messages` / `conversation-max-content-length`）
- 管理端审计 UI：plan 标为二期可选，当前仍仅 API

## Main spec

无 delta spec；未同步至 `openspec/specs/`。若需正式能力文档，应新开 change 按现行 API 重写。
