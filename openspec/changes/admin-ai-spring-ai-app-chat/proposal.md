# Admin AI：Spring AI + app-chat

## 动机

将 kiwi-admin 的 AI 对话后端从手写 HTTP 调用改为 **Spring AI**，与 Spring Boot 生态对齐；前端统一使用布局中已有的 **`app-chat`** 浮层对话框实现，避免重复 UI。

## 范围

- 后端：引入 `spring-ai-openai-spring-boot-starter`，用 `ChatModel` 完成多轮对话；保留 `/ai/chat` 契约（登录后可调）。
- 前端：`ChatComponent`（`app-chat`）改为调用 `/ai/chat`；支持 `embed` 嵌入模式供仪表盘路由页复用；`/default/dashboard/ai-chat` 仅作面包屑 + 嵌入组件。

## 非目标

- 流式 SSE、工具调用、多模态。
- 替换系统菜单/权限数据（仍可在菜单中配置路由）。
