---
name: 最近组件按需解析
overview: 「最近使用的组件」不落库；仅在查询时从当前用户已保存的流程（BPMN XML）中解析并聚合，按流程更新时间推导最近顺序与属性快照。
todos:
  - id: remove-persistence
    content: 移除或停用 BpmComponentRecentUsage 实体/Dao、save 钩子；删除 recordFromBpmnXml 写库逻辑（若已实现）
  - id: query-processes
    content: 在 BpmComponentRecentUsageService（或重命名）中按 createdBy=当前用户查询 BpmProcess，按 updatedTime 降序，可设 limit（如 50）控制成本
  - id: merge-algorithm
    content: 逐流程解析 bpmnXml，复用 extractSnapshotsByComponentId；按流程从新到旧合并，componentId 首次出现即作为该组件的「最近快照」（代表含该组件的流程中保存最新的一份）
  - id: api
    content: GET /bpm/component/recent-usage 仅调用上述计算并返回 DTO 列表
  - id: frontend
    content: 前端仍请求 recent-usage 填充 append 弹层「最近使用」分组；追加时应用 entries 快照
---

# 最近使用组件：仅查询时从已保存流程提取（不持久化）

## 需求变更（相对原方案）

- **不再**将最近使用写入 Mongo（不使用 `BpmComponentRecentUsage` 集合、`recordFromBpmnXml` 在保存流程时落库）。
- **仅在**客户端请求「最近使用」时，服务端从**该用户保存过的流程**的 `bpmnXml` 中解析、聚合后返回。

## 数据范围

- 流程筛选：使用 [`BpmProcess`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/model/BpmProcess.java) 继承的 `BaseEntity` 中的 `createdBy`，查询 `createdBy = 当前用户 id` 的流程（需在 [`BpmProcessDefinitionDao`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/dao/BpmProcessDefinitionDao.java) 增加 `findByCreatedByOrderByUpdatedTimeDesc`，或等价查询）。
- 若产品要求「项目内所有流程」而非仅本人创建，需另行约定（当前按用户创建流程理解）。

## 聚合算法（推荐）

1. 查询该用户流程，按 `updatedTime` **降序**，限制条数 `limit`（防止流程极多时单次请求过慢）。
2. 对每个流程的 `bpmnXml` 调用现有解析逻辑（[`BpmComponentRecentUsageService#extractSnapshotsByComponentId`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/service/BpmComponentRecentUsageService.java) 或等价），得到 `Map<componentId, List<PropertySnapshotEntry>>`（单流程内同 id 后者覆盖由解析侧保证）。
3. **合并顺序**：按流程从新到旧遍历，维护 `LinkedHashMap<componentId, snapshot>`：**仅当 componentId 尚未出现时**才放入。这样每个组件绑定的是「在仍被遍历到的、时间最新的流程里」出现的那份快照，等价于「最近保存且包含该组件」的配置。
4. 返回列表顺序即 `LinkedHashMap` 的插入顺序（最近使用的组件优先）。

## 性能与缓存

- **无 DB 表**；可选 **短时内存缓存**（同一用户、60s 内重复 GET 不重复扫 XML）——若需要再加，默认可先不做。
- 解析逻辑应保持轻量；`limit` 必配。

## 需删除/调整的文件（相对已起草的持久化代码）

- [`BpmComponentRecentUsage.java`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/model/BpmComponentRecentUsage.java)、[`BpmComponentRecentUsageDao.java`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/dao/BpmComponentRecentUsageDao.java)：若仅用于最近使用，可删除；若保留 DTO 类型，可迁到 `dto` 包或由 Service 内部类承担。
- [`BpmProcessDefinitionCtl#saveProcessDefinition`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/ctl/BpmProcessDefinitionCtl.java)：不应再调用任何「写最近使用表」的逻辑。

## 前端

- 行为不变：请求 `GET /bpm/component/recent-usage` 展示「最近使用」；选中项带 `entries` 时在 `initElement` 之后应用快照。
