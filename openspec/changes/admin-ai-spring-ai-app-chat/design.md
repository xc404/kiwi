# 设计

## 后端

- **依赖**：`spring-ai-bom`（import）+ `spring-ai-openai-spring-boot-starter`。
- **配置**：使用 `spring.ai.openai.*`（`api-key`、`base-url`、`chat.options.model`），与 Spring AI 文档一致；`kiwi.ai.enabled` 仅作业务开关，在 Service 内短路。
- **调用链**：`AiChatCtl` → `AiChatService` → `ChatModel#call(Prompt)`；将 `AiChatMessage` 转为 `UserMessage` / `AssistantMessage` / `SystemMessage`。

## 前端

- **`ChatComponent`**：`messageArray` 仍为 UI 状态；发送前组装 `AiChatMessage[]`（右=user，左=assistant）；订阅 `AiChatService.chat()`，错误用 `NzMessageService`。
- **嵌入模式**：`embed` 为 `input(false)`；为 `.chat-wrap` 增加 `.embedded` 样式，脱离 `fixed` 底栏布局，供仪表盘页全宽/居中展示。
- **`AiChatComponent` 页面**：仅面包屑 + `<app-chat [embed]="true" />`，删除重复气泡 UI。
