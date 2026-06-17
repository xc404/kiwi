# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/remove-engine-rest-owned-only_5360d400.plan.md`；**已实现**后归档，plan 文件已删除。

## 实现路径（以代码为准）

- **实例只读 API**：`GET /bpm/process-instance/{id}/definition-xml`、`history-activities`、`variables`（`BpmProcessInstanceCtl` + DTO）
- **所有权**：`BpmOwnershipAccessService`（`BpmProcess.createdBy`）；流程/实例 CRUD 与列表过滤；`bpm:admin` 可跳过（见服务实现）
- **engine-rest HTTP**：`kiwi.bpm.engine-rest-http-enabled` 默认 `false`；`EngineRestHttpBlockConfiguration` 注册 403 Filter
- **前端**：`process-instance.service` 走 `/bpm/*`；已移除 `camundaEngineRestPath`；`frontend/README.md` 已说明
- **cryoEMS**：`state` / `batchStates` / `complete` 等机机端点按设计可豁免所有权（见 `BpmProcessInstanceCtl`）

## 未正式闭环

- plan 验证清单（跨用户 403、cryoEMS 冒烟）无自动化测试记录，见 `tasks.md` 最后一项

## Main spec

无 delta spec；`openspec/specs/backend-api-security/spec.md` 等未单独收录本变更细节。
