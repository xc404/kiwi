## Why

流程实例列表已支持默认「仅运行中」（`unfinished`），但缺少**已结束**实例的独立筛选；业务排查、审计与对账时常需要只看已完成的流程。需要在规格层明确「运行中 / 已结束 / 全部」的语义与 API，避免仅依赖 `unfinished=true|false` 时「false=全部」与「仅已结束」混淆。

## What Changes

- **后端** `GET /bpm/process-instance`：用明确的**实例状态**查询参数（见 design）映射到 Camunda `HistoricProcessInstanceQuery` 的 `unfinished()` / `completed()` / 无附加条件，默认仍为**仅运行中**。
- **前端** `bpm-process-instances`：在搜索区提供「实例状态」条件（运行中 / 已结束 / 全部），默认运行中；与 `search.basicParams` 及 crud 合并规则一致。
- **规格**：新增 `bpm-process-instance-list` 能力要求与场景（见 `specs/`）。

## Capabilities

### New Capabilities

- `bpm-process-instance-list`：列表查询支持按实例生命周期状态筛选（运行中、已结束、全部）。

### Modified Capabilities

- （无）

## Impact

- **代码**：`BpmProcessInstanceCtl.java`、`bpm-process-instances.ts`；可能与现有 `unfinished` 查询参数做一次兼容或替换（以 design 为准）。
- **行为**：默认仍为仅运行中；用户显式选择「已结束」或「全部」时结果集变化符合 Camunda 语义。
