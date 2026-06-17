# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/slurm-flag-handler-inline.plan.md`；plan 文件已删除。

## 未按本 plan 实施

本 plan 目标：将 `Processor` 逻辑内聚进 `SlurmFlagFileHandler`，`SlurmTaskManager` 仅注入 handler Bean，**不**新增 `SlurmFlagExternalTaskSupport`。

**实际演进**：后续改为 **sacct + Mongo 跟踪** 作为唯一终态来源，`.flag` 文件监听路径已移除。见归档 change `2026-06-17-slurm-job-mongo-only`。

## 当前代码（以 `kiwi-bpmn-component-slurm` 为准）

- **`SlurmFlagFileHandler` 已删除**（非内聚重构）
- `SlurmTaskManager`：仅 sbatch 提交 + `SlurmJobTracker` Mongo 登记；无 `startFlagWatcher` / `FileAlterationMonitor`
- 终态上报：`SlurmSacctParser` → `SlurmJobCompleteProcessor`
- `flag-listener-enabled=true` 仅打 warn 并忽略

**不得**按本目录 design 恢复 flag 监听；若需文档化 Slurm 终态，以 sacct 架构与 `slurm-terminal-completion-entrypoint` spec 为准。

## Main spec

无 delta spec。
