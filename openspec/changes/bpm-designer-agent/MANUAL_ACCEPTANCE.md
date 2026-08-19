# BPM 设计器 Agent — 手工验收清单（A1–A9）

> 前置：`kiwi.bpm.designer-agent.enabled=true`，`kiwi.ai.enabled=true` 且已配置 API Key；打开 BPM 设计器（`/bpm/design/:id`），使用右侧 Agent 面板。

| # | 用户说法 | 预期行为 | 通过 |
|---|----------|----------|------|
| A1 | 「加一个 HTTP 请求节点」 | MCP 查 `httpRequest` → EditPlan `addNode` → 预览导入画布；简单操作可跳过 Plan 闸门 | ☐ |
| A2 | 选中节点后「删掉这个节点」 | EditPlan `removeNode` + 相关 `removeFlow` → 预览 | ☐ |
| A3 | 「把 command 改成 xxx」（选中 Shell 节点） | EditPlan `updateNode.parameters` → 预览 | ☐ |
| A4 | 「用通知组件替换邮件节点」 | MCP 查组件 → `updateNode.componentId` + 参数合并 → Plan 审阅（若复杂）→ 预览 | ☐ |
| A5 | 「在 A 和 B 之间插入任务」 | `addNode` + `addFlow` + `removeFlow` → 预览 | ☐ |
| A6 | 「帮我做一个下单流程：创建订单→支付→通知」 | 多步 EditPlan → **Plan 卡片**审阅 → 批准后 apply → 预览 | ☐ |
| A7 | 「参考某流程的 HTTP 配置」 | MCP `bpmPd_get` 读源 → EditPlan 合并参数 → 预览 | ☐ |
| A8 | 「需要 kafka 组件」 | MCP `bpmRemoteMarket_list` → `await_install` 卡点（或 install 提示） | ☐ |
| A9 | 「保存 / 部署 / 跑一下」 | 预览确认后保存；部署/启动需 MCP 写 API 或用户手动点工具栏 | ☐ |

## SSE / UX 附加检查

| 项 | 预期 | 通过 |
|----|------|------|
| 思考过程 | 面板「思考过程」折叠块可见 `stage` / `thinking_delta` | ☐ |
| MCP 工具轨迹 | 调用 MCP 时出现 `tool_start` / `tool_end` 行（🔧 / ✓） | ☐ |
| Plan 续推 | 批准 Plan 后 SSE 续订，可见 apply / validation / preview 阶段事件 | ☐ |
| 全局 Chat | `app-chat` 改图路径未回归；设计器不再出现 `bpm-ai-chat` | ☐ |

## 备注

- A10（只读解释）已在编排器 `readOnly` 分支覆盖，可在 A1 前快速验证：「解释一下这个流程干什么」应直接 `done`，无预览。
- 自动化：`EditPlanApplicatorTest`、`DesignerAgentOrchestratorSseTest` 覆盖 apply 链与 SSE 事件；本清单用于端到端人工回归。
