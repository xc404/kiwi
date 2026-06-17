## Why

「最近使用的组件」若写入 Mongo，与流程保存耦合、数据易 stale。改为仅在查询时从用户已保存流程的 BPMN XML 解析，可反映真实最近配置且无需维护独立集合。

## What Changes

- 移除（或不实现）`BpmComponentRecentUsage` 持久化；保存流程时不再写最近使用表
- `BpmComponentRecentUsageService`：按 `createdBy` 查流程、`updatedTime` 降序、limit 50，合并 componentId 快照
- `GET /bpm/component/recent-usage` 返回计算结果
- 前端 append/replace 弹层「最近使用」分组不变

## Capabilities

### New Capabilities

- （无 main spec。）

### Modified Capabilities

- （无。）

## Impact

- `BpmComponentRecentUsageService`、`BpmComponentCtl`、`BpmProcessDefinitionDao`
- 前端 `process-design.service.ts`、context-pad 模块

## 非目标

- 项目内全员流程（当前仅 `createdBy = 当前用户`）
- 查询结果短时缓存（可选，未实现）
