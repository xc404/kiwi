# Design

## 原则

- **仅 Mongo**：`MongoRepository<SlurmJob, String>`，无 `ConcurrentHashMap` 回退
- **终态来源 sacct**：弃用工作目录 `*.flag` 监听（默认关闭/已移除）
- Slurm `enabled=true` 时须能装配 Repository + Tracker（`MongoTemplate` 在 classpath）

## 实体

`SlurmJob`（`slurm_job` 集合）：

- `id` 与 `jobId` 一致
- `externalTaskId`、`workerId`、状态、过期时间等
- 继承 `BaseEntity` 审计字段

`SlurmJobTracker`：

- `saveTrackedJob` 提交后落库
- 定时 `SlurmSacctClient` 批量查询 → `SlurmSacctParser` → `SlurmJobCompleteProcessor` 上报 Camunda 外部任务终态
- 超时按 `maxTrackDurationMs` 失败上报

## Flag 机制弃用

| 项 | 行为 |
|----|------|
| `startFlagWatcher` | 已移除 |
| sbatch 追加 `.flag` | 已移除 |
| `flag-listener-enabled` | 配置存在则 warn，无效果 |
| `SlurmService` 脚本 | 用户命令直接执行；退出码以 sacct `.batch` ExitCode 为准 |

## 装配

```text
kiwi.bpm.slurm.enabled=true (默认)
  → SlurmEnabledConditionsValidator 校验 workDirectory + Mongo beans
  → @ConditionalOnClass(MongoTemplate) 注册 SlurmJobTracker + Repository
```

`sacct` 关闭时：Tracker 不轮询（`sacct == null` 分支）；plan 推荐 sacct 关时不访问库——实现为 Slurm 总关或 Mongo 缺失则整模块不装配跟踪。

## 测试

- `SlurmJobTrackerTest` 等 Mock Repository
- 不再测试「无库走 Map」分支

## 部署注意

- 共享盘不再依赖 flag 文件
- submit 节点须 `sacct` 可见作业记账
