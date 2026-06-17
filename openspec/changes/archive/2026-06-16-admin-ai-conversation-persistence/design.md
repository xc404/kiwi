# Design

## 数据模型

- `AiChatConversation` 继承 `BaseEntity`，集合名 `ai_chat_conversation`
- `messages` 仅含 `user` / `assistant`；BPM 的 system 上下文仍由 `messagesEnricher` 在请求时注入
- `scope`：`global` | `bpm-designer`；`scopeRef` 可选（如 processId）

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/ai/conversations` | 当前用户分页列表（列表响应清空 messages） |
| GET | `/ai/conversations/{id}` | 详情 |
| POST | `/ai/conversations` | 创建 |
| PUT | `/ai/conversations/{id}` | `mode=append\|replace` 更新消息或标题 |
| DELETE | `/ai/conversations/{id}` | 删除 |
| GET | `/ai/conversations/audit` | 需 `ai:conversation:audit` |

## 容量

- `kiwi.ai.conversation-max-messages`（默认 200）
- `kiwi.ai.conversation-max-content-length`（默认 32000）

## 前端

- `AiConversationService` + `ChatComponent` 标题栏历史/新建/删除
- `localStorage` 记录各 scope 下上次打开的 `conversationId`
