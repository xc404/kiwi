# Design

## 原则

- **不持久化**最近使用记录
- **仅 GET** 时从该用户流程的 `bpmnXml` 解析、聚合

## 数据范围

- `BpmProcess.createdBy = 当前用户 id`
- 按 `updatedTime` 降序，`MAX_PROCESSES = 50`

## 聚合算法

1. 查询用户流程（分页 limit）
2. 每流程 `extractSnapshotsByComponentId(bpmnXml)` → `Map<componentId, snapshots>`
3. 按流程从新到旧遍历，`LinkedHashMap`：**componentId 首次出现**即采纳（绑定「最近保存且含该组件」的快照）
4. 返回 `List<RecentBpmComponent>`，顺序即插入顺序

单流程内：前序遍历 serviceTask/callActivity，同 componentId 后者覆盖前者。

## API

`GET /bpm/component/recent-usage`（`@SaCheckLogin`）

响应：`RecentBpmComponent extends BpmComponent`，额外字段 `lastUsedFromProcessAt`。

快照应用：`applySnapshotToParameterDefaults` 覆盖同 key 参数的 `defaultValue`（input/output/In/Out）。

## 前端

- `GET /bpm/component/recent-usage` → context-pad「最近使用」
- 选中组件后 `initElement` 使用返回的参数默认值

## 性能

- 无 DB 表；每次请求扫描最多 50 份 XML
- 可选 60s 内存缓存（未实现）
