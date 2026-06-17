## ADDED Requirements

### Requirement: Slurm 提交全局并发上限

系统 SHALL 在调用 `sbatch` 之前，按 Mongo 集合 `slurm_job` 中 `status=Running` 的记录条数判断当前 Slurm 作业并发数；当并发数达到或超过 `kiwi.bpm.slurm.max-concurrent-jobs` 时，系统 MUST 拒绝本次提交，且 MUST NOT 真正执行 `sbatch`、MUST NOT 写入 `slurm_job` 跟踪文档。

当 `kiwi.bpm.slurm.max-concurrent-jobs <= 0` 时，系统 SHALL 跳过该闸门，行为与未启用限流时完全等价。

#### Scenario: 未达上限正常提交

- **WHEN** External Task Client 拉取到 topic `slurm` 的任务，且 Mongo `slurm_job{status:Running}` 计数严格小于 `kiwi.bpm.slurm.max-concurrent-jobs`
- **THEN** 系统按现有路径构造 `SbatchConfig`、调用 `sbatch`、写入 Mongo 跟踪文档，并通过 `complete` 提交 External Task 变量

#### Scenario: 达到上限拒绝提交

- **WHEN** External Task Client 拉取到 topic `slurm` 的任务，且 Mongo `slurm_job{status:Running}` 计数大于等于 `kiwi.bpm.slurm.max-concurrent-jobs`
- **THEN** 系统抛出 `SlurmOverloadedException`；本次 `executeAsync` 不调用 `sbatch`，不写入 Mongo `slurm_job`，并由 `AbstractExternalTaskHandler` 的统一 catch 走 `handleFailure` 流程

#### Scenario: 限流关闭等价于禁用

- **WHEN** `kiwi.bpm.slurm.max-concurrent-jobs` 配置为 `0` 或负值
- **THEN** 系统不查询 Mongo 计数、不抛过载异常，提交链路与未引入闸门时完全一致

### Requirement: 非递减重试接口

`IRetry` SHALL 提供默认方法 `decreaseRetries()`，默认返回 `true`，表示"本次失败应递减 `retries`"。`IRetry` 的实现类 MAY 覆盖该方法返回 `false`，表示"本次失败不消耗 `retries` 配额"。`SlurmOverloadedException` MUST 实现 `IRetry` 并覆盖 `decreaseRetries()` 返回 `false`。

#### Scenario: IRetry 默认实现保持向后兼容

- **WHEN** 一个类型仅 `implements IRetry`、不覆盖 `decreaseRetries()`
- **THEN** `decreaseRetries()` 返回 `true`，规划器按现有的"标准 cycle + 递减 retries"分支处理

#### Scenario: SlurmOverloadedException 标记为非递减

- **WHEN** 调用 `new SlurmOverloadedException("...").decreaseRetries()`
- **THEN** 返回 `false`

### Requirement: 非递减重试分发与保留 retries

系统 SHALL 在 `ExternalTaskRetryPlanner.plan(task, failure)` 中，沿失败异常链定位首个 `IRetry` 实例；当其 `decreaseRetries()` 返回 `false` 时 MUST 保留 External Task 当前的 `retries` 值；当 `retries` 为 `null`（首次失败）时 MUST 将 `nextRetries` 设为 `1`。

#### Scenario: 已有 retries 保留

- **WHEN** External Task 之前已发生过失败，`task.getRetries()` 为正整数 N，本次失败为 `SlurmOverloadedException`（或任一 `decreaseRetries()=false` 的 `IRetry`）
- **THEN** `plan(task, failure)` 返回的 `nextRetries == N`（与入参一致，不减 1）

#### Scenario: 首次失败使用固定初值 1

- **WHEN** `task.getRetries()` 为 `null` 且本次失败为 `SlurmOverloadedException`
- **THEN** 返回的 `nextRetries` 等于 `1`

#### Scenario: decreaseRetries 默认为 true 时走标准分支

- **WHEN** 失败异常实现 `IRetry` 但未覆盖 `decreaseRetries()`，`task.getRetries()` 为 4
- **THEN** 走标准 BPMN cycle 分支，返回的 `nextRetries == 3`（递减 1）

### Requirement: 非递减重试分支使用独立退避周期

系统 SHALL 在非递减重试分支优先使用 `kiwi.bpm.external-task-retry.non-decreasing-retry-cycle`（纯 ISO-8601 duration，默认 `PT30S`）计算 `retryTimeoutMs`；当其为空或解析失败时 MUST 回退到 BPMN / 引擎默认 cycle 的第一个间隔；当二者都解析失败时 MUST 兜底为 30000 毫秒。

#### Scenario: 解析配置的退避周期

- **WHEN** `kiwi.bpm.external-task-retry.non-decreasing-retry-cycle = PT30S` 且本次失败为 `SlurmOverloadedException`
- **THEN** 返回的 `retryTimeoutMs` 大于 0 且不超过 30000 毫秒

#### Scenario: 未配置退避周期时的兜底

- **WHEN** `kiwi.bpm.external-task-retry.non-decreasing-retry-cycle` 为空且 BPMN / 引擎默认 cycle 均解析失败
- **THEN** 返回的 `retryTimeoutMs` 等于 30000

### Requirement: 过载事件可观测

系统 SHALL 在闸门触发时输出 `WARN` 级日志，日志字段必须至少包含：当前 Running 计数、`maxConcurrentJobs` 阈值、`processInstanceId`、`activityId`、`externalTaskId`、`businessKey`。

#### Scenario: 过载日志包含必要字段

- **WHEN** 闸门拒绝一次提交
- **THEN** 日志输出格式可被 grep `Slurm overloaded`，且单行中能解析出上述全部字段（缺失字段以 `null` / 空串占位但 key 仍存在）
