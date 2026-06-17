## 1. OpenSpec 工件

- [x] 1.1 建立 change `admin-ai-client-action-handlers` 的 proposal / design / specs / tasks

## 2. 前端：编排层与组件改造

- [ ] 2.1 新增 `shared/ai-assistant`：`AssistantActionHandler`、`AssistantActionContext`、`ASSISTANT_ACTION_HANDLERS` token、`NavigateAssistantActionHandler`、`AssistantActionOrchestratorService`
- [ ] 2.2 改造 `ChatComponent`：使用 `actionHandlers` input + orchestrator；移除内联 `applyAssistantActions`
- [ ] 2.3 在 `frontend/README.md`「AI 辅助」节补充 `actionHandlers` / 多提供者扩展说明（一句 + 指向 `design.md`）

## 3. 验证

- [ ] 3.1 在 `kiwi-admin/frontend` 执行 `npm run build`（或项目等价命令）确保编译通过
