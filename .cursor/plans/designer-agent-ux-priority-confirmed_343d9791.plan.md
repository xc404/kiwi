# Designer Agent UX：P0/P1 优先级确认（实施序）

**来源**：`designer-agent-ux-pain-points_343d9791.plan.md`  
**确认结论**（按本轮实施顺序）：

| 优先级 | 主题 | 决策 |
|--------|------|------|
| **P0-1** | 功能未启用 | 前端 `GET /config` 探测 + 面板内禁用/说明，避免裸 API 失败 |
| **P0-2** | `await_install` 卡死 | Graph 可续跑 + `resume_install` / `skip_install` + 安装闸门卡片 |
| **P0-3** | 预览/会话恢复绑 Tab | 进程加载即 `by-target`；`await_preview` 画布导入不依赖 Agent Tab |
| **P1-A** | 闸门 UX | Plan 拒绝需反馈；Plan/Preview/Ask/Install 统一卡片样式；Ask 专用卡片 |
| **P1-B** | 会话 polish | 空状态、自动滚底、澄清阶段输入不禁用、busy 时仍可草稿（澄清） |
| **P1-C** | `human_clarify` | Graph 节点 + `submit_clarification` 双通道（选项 + 补充文字） |
| **远期** | Cursor 式 HITL | 本迭代：`pendingHitlItems` 契约 + SSE `hitl_pending` + 时间线内联渲染；工具环 interrupt 留后续 |

**human_clarify vs 闸门 vs 恢复**：澄清在 **generate 之前**（需求歧义）；Plan/Preview 在 **generate 之后**（变更审阅）。恢复与 enabled 探测优先于 polish。
