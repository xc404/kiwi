## Why

Slurm 外部任务终态跟踪曾支持内存 Map 与 `.flag` 文件双路径，行为不一致、多实例不可靠。需统一为 **Mongo 持久化 + sacct 轮询**，弃用 flag 机制。

## What Changes

- `SlurmJob` 落 Mongo（`slurm_job`）；`SlurmJobRepository` 为唯一存储
- `SlurmJobTracker` 提交后 `save`、定时 sacct 轮询、终态 `delete`/状态更新
- 移除/停用 `.flag` 文件监听与 sbatch 写 flag 逻辑
- Slurm 启用时启动期校验 Mongo 与 Repository 可装配（不静默回退内存）
- 单测 Mock `SlurmJobRepository`，覆盖 sacct 路径

## Capabilities

### New Capabilities

- （无独立 main spec；见 slurm 相关既有 specs。）

### Modified Capabilities

- （无 delta 同步。）

## Impact

- `kiwi-bpmn-component-slurm`：`SlurmJob`、`SlurmJobTracker`、`SlurmTaskManager`、`SlurmService`、`SlurmAutoConfiguration`
- 部署：submit 机需可执行 `sacct`；Slurm 开启需 Mongo

## 非目标

- 关 Slurm 时强制无 Mongo（应用其它功能仍可用 Mongo）
