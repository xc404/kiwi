# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/recent-components-on-demand.plan.md`；**已实现**后归档，plan 文件已删除。

## 实现路径（以代码为准）

- **不落库**：无 `BpmComponentRecentUsage` 实体/集合；`BpmComponentRecentUsageService.listForCurrentUser` 按需扫描 BPMN
- `findByCreatedByOrderByUpdatedTimeDesc(userId, limit=50)` → 逐流程 `extractSnapshotsByComponentId` → `LinkedHashMap` 合并（componentId 首次出现保留）
- `GET /bpm/component/recent-usage` → `RecentBpmComponent`（含 `lastUsedFromProcessAt`）
- 快照写入返回 DTO 的 `inputParameters`/`outputParameters` 的 `defaultValue`（非独立 `entries` 字段）
- 前端 `process-design.service` + context-pad append/replace「最近使用」分组

## 与 plan 的差异

- plan 提及前端「entries 快照」；实现为后端合并进参数 `defaultValue`，前端消费 `ComponentDescription` 即可
- 短时内存缓存（60s）**未做**

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
