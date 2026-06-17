## Why

`SlurmFlagFileHandler` 通过嵌套 `Processor` 接口委托 `SlurmTaskManager` 完成外部任务 complete/failure，耦合绕圈且易引入 `SlurmFlagExternalTaskSupport` 等中间类。拟将 flag 路径逻辑内聚到 handler 自身，由 Spring 注册单例 Bean。

## What Changes（plan 草案，未实施）

- `SlurmFlagFileHandler` 构造注入 `ProcessEngine` 等依赖，私有方法承载 complete/failure 链路
- 删除嵌套 `Processor`；`SlurmTaskManager` 仅注入 handler 并在 `startFlagWatcher` 注册 listener
- `SlurmAutoConfiguration` 声明 `@Bean SlurmFlagFileHandler`

## 实际替代方案

本 refactor **未落地**；由 `slurm-job-mongo-only` 移除 flag 机制，改用 sacct 轮询 + `SlurmJobCompleteProcessor`（见 `archive/2026-06-17-slurm-job-mongo-only`）。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- （无。）

## Impact（plan 原意）

- `kiwi-bpmn-component` 内 `SlurmFlagFileHandler`、`SlurmTaskManager`、`SlurmAutoConfiguration`
