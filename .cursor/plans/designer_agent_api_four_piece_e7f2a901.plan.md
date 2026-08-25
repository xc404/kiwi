# Designer Agent 对外接口四件套迁移

**状态：已完成**（2026-08-25）

## 目标契约

| # | 方法 | 路径 | 职责 |
|---|------|------|------|
| 1 | POST | `/bpm/designer-agent/runs` | 创建 run，JSON 返回 `DesignerAgentRunStatus`，异步启动 Graph |
| 2 | GET | `/bpm/designer-agent/runs/{runId}` | **唯一状态源**（checkpoint 投影） |
| 3 | POST | `/bpm/designer-agent/runs/{runId}/actions` | 人机操作：`confirm_plan` / `confirm_preview` / `answer` |
| 4 | GET | `/bpm/designer-agent/runs/{runId}/events` | 可选事件流，**仅**推送思考/工具/文本，不重放历史 |

辅助：`GET /bpm/designer-agent/by-target?targetProcessId=` 保留。

## 分层原则

- **Graph checkpoint** = run 状态真相源
- **GET /runs/{id}** = 对外状态契约
- **GET /events** = 仅日志/思考，不驱动闸门 UI
- 前端闸门按钮：先 GET 预检 stage → POST actions → 必要时重连 events

## 废弃（保留兼容）

- `POST /runs/stream` → POST `/runs` + GET `/events`
- `POST /runs/{id}/stream/resume` → GET `/events`（不重放）
- `POST .../confirm-plan|confirm-preview|answer` → POST `/actions`

## 手动改画布

- `POST /actions` 可选字段 `canvasBpmnXml`：以**当前画布**为准
- `confirm_preview` 确认：保存 `canvasBpmnXml`（含用户微调），非仅服务端 `candidateXml`
- `confirm_plan` / `answer`：同步 `baseBpmnXml` 到 Graph checkpoint 后再 resume
- 前端预览拒绝：`rejectAgentPreview()` 整图回退（非单步 undo）
- 预览加载前若检测到 run 期间有手动改图，提示「确认保存时以当前画布为准」


- 后端：`DesignerAgentCtl`、`DesignerAgentSessionService`、`DesignerAgentRunActionRequest`
- 前端：`bpm-designer-agent.service.ts`、`bpm-designer-agent.component.ts`
- 评测：`DesignerAgentEvalClient.java`
