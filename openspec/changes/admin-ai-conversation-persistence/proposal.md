# Proposal: AI 助手会话 Mongo 持久化

## 背景

`ChatComponent` 原先仅在内存中保存消息，刷新或换设备后丢失。需要服务端持久化以支持跨设备恢复、检索与审计。

## 范围

- Mongo 集合 `ai_chat_conversation`，消息结构与 `AiChatMessage` 对齐
- REST `/ai/conversations` CRUD；`GET /ai/conversations/audit` 供管理员跨用户查询
- 普通用户仅 `@SaCheckLogin` + `ownerId` 隔离；审计权限 `ai:conversation:audit`
- 前端 `ChatComponent` 集成会话列表、新建、切换、删除与发送后自动保存
- BPM 设计器使用 `scope=bpm-designer` + `scopeRef=processId`，不持久化 system 上下文

## 非目标

- 管理端审计 UI（一期仅 API）
- 修改 `/ai/assistant` 无状态推理流程
