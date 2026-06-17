## Context

Camunda `HistoricProcessInstanceQuery` 提供：

- `unfinished()`：未结束 → **正在运行**
- `completed()`：已结束 → **已结束**
- 两者均不加：历史库中的**全部**实例（数据量可能很大）

当前实现使用布尔 `unfinished`（默认 `true`）；`false` 表示不加 `unfinished()`，即全部，**无法单独筛「仅已结束」**。

## Goals / Non-Goals

**Goals:**

- 支持三种互斥状态：**运行中**、**已结束**、**全部**；默认**运行中**。
- 与 Camunda 语义一致，避免组合出非法查询（不得同时 `unfinished` 与 `completed`）。

**Non-Goals:**

- 不要求改为 Camunda HTTP 自调用；仍可用 `ProcessEngine`。
- 不在本变更中扩展结束时间区间、按业务键以外的复杂组合检索（可后续单独立项）。

## Decisions

1. **查询参数**：采用单一枚举参数 **`instanceState`**（建议取值 `running` | `completed` | `all`），默认 `running`。废弃或弃用单独布尔 `unfinished`，避免与「全部」混淆；若短期需兼容，可将 `unfinished=true` 映射为 `running`，`unfinished=false` 映射为 `all`，并在文档中标注弃用。
2. **后端映射**：
   - `running` → `query.unfinished()`
   - `completed` → `query.completed()`
   - `all` → 不调用上述二者
3. **前端**：搜索表单增加「实例状态」下拉（或分段控件），选项文案：**运行中** / **已结束** / **全部**；默认值 **运行中**，写入 `basicParams` 或表单字段 `instanceState=running`，与分页、其它筛选字段合并策略不变（`basicParams` 先合并，表单覆盖同名键）。
4. **查看详情**：列表「查看」操作在**浏览器新标签页**打开流程实例查看页（`#/bpm/process-instance/:id`），使用 `window.open(..., '_blank', 'noopener,noreferrer')`，与哈希路由一致。

## Risks / Trade-offs

- **[Risk]** `all` 在大库上全表扫历史 → **Mitigation**：UI 提示或仅管理员可见「全部」（可选，非本变更必做）。
- **[Risk]** 兼容旧 `unfinished` 参数 → **Mitigation**：过渡期双读，优先 `instanceState`。

## Migration Plan

- 实现 `instanceState` 后更新前端去掉仅依赖 `unfinished: true` 的 `basicParams`，改为 `instanceState: 'running'`（或等价）。
- 集成测试或手工：三种状态下列表条数与 Camunda Cockpit / REST 抽样一致。

## Open Questions

- 无。
