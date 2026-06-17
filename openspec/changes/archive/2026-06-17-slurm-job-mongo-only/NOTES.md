# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/slurmjob-mongo-only.plan.md`；**已实现**后归档，plan 文件已删除。

## 实现路径（以代码为准）

- 模块：**`kiwi-bpmn-component-slurm`**（非 plan 中的 `kiwi-bpmn-component`）
- `SlurmJob` `@Document(collection="slurm_job")` + `SlurmJobRepository`；**仅 Mongo**，无内存 Map 分支
- `SlurmJobTracker`（plan 称 `SlurmJobCompletionTracker`）构造注入 Repository，sacct 轮询终态
- `SlurmEnabledConditionsValidator`：Slurm 开启时要求 Mongo + Tracker/Repository 可装配
- **`.flag` 监听已移除**（非仅 `@Deprecated`）；`flag-listener-enabled=true` 打 warn 并忽略
- `SlurmService` 脚本不再追加 flag 行；终态由 `SlurmSacctParser` + `SlurmJobCompleteProcessor` 上报
- `spring-boot-starter-data-mongodb` 在 slurm 模块 `pom.xml`

## 与 plan 的差异

- 无独立 `kiwi.bpm.slurm.sacct.enabled`：sacct 配置在 `SlurmProperties.Sacct` 嵌套对象，Slurm 启用且 Mongo 在 classpath 即注册跟踪
- `SlurmTrackedExternalJob` / `SlurmFlagFileHandler` 已删除，非保留弃用类
- 终态协调类为 `SlurmJobCompleteProcessor`（非 plan 中的 `SlurmExternalTaskTerminalCoordinator` 命名）

## Main spec

无 delta spec；与 `openspec/specs/slurm-terminal-completion-entrypoint/` 等既有 spec 互补，本归档记录 Mongo-only 跟踪迁移决策。
