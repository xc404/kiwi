## Context

`SlurmExternalTaskHandler` 订阅 topic `slurm`，被 Camunda External Task Client fetch 后立即调用 `SlurmTaskManager.submitSlurmJob`，进而通过 `ProcessBuilder("sbatch", ...)` 提交作业并写入 Mongo `slurm_job` 集合做 sacct 跟踪。

现状关键约束：

- `SlurmJobTracker` 周期性 sacct 轮询，跟踪窗口由 `SlurmJob.expiration` 决定；超时未观察到终态会被本系统当作失败上报（`SlurmJobResult.STATE_TRACKING_EXPIRED`）。所以让 Slurm 本身用 QoS/分区限额把作业长期挂在 `PD` 上，会与本系统跟踪机制冲突。
- `AbstractExternalTaskHandler` 抛异常时统一调用 `externalTaskService.handleFailure(task, msg, details, retries, retryTimeoutMs)`，重试计划由 `ExternalTaskRetryPlanner` 计算。
- `ExternalTaskRetryPlanner.plan(task, failure)` 已经对 `OptimisticLocking` 走"保留 retries"的特殊分支，是引入"过载保留 retries"的天然挂载点。

## Goals / Non-Goals

**Goals:**

- 在 `sbatch` 真正发生之前实施全局并发上限，超额时不消耗 Slurm 资源、也不污染 Mongo 跟踪。
- 过载回退使用与业务失败重试解耦的退避周期，且不消耗业务 `retries`。
- 默认配置对现有部署"零感知"：不开启限流时行为完全等价于改动前。
- 不阻塞 External Task worker 线程，不增加 lockDuration 占用时间。

**Non-Goals:**

- 不实现 per-partition / per-taskType / per-tenant 的细粒度限流（按用户选择"全局一个上限"）。
- 不引入 Slurm 端 QoS 配置变更指引（与本仓库无关，运维侧自行决定是否叠加）。
- 不在 squeue/sacct 上做兜底校验（按用户选择"仅 Mongo 计数"，避免额外开销与权限风险）。
- 不修改 sacct 跟踪或 expiration 逻辑。

## Decisions

### 1. 闸门位置：放在 `SlurmExternalTaskHandler.executeAsync` 提交前

**选项 A**（采用）：在 `executeAsync` 的同步路径中，构造 `SbatchConfig` 之前调用 repository 计数。超阈值则抛 `SlurmOverloadedException`，直接被 `AbstractExternalTaskHandler.execute` 的 `catch` 捕获 → `handleFailure`。
**选项 B**（不采用）：放在 `SlurmTaskManager.submitSlurmJob` 内。该方法异步执行，抛出异常需要走 `CompletableFuture` 链，最终也能到达同一 catch，但增加 thread pool 调度开销，过载场景下没必要。
**选项 C**（不采用）：在 External Task Client fetch 阶段过滤。Camunda 客户端 fetch 是 topic 级、无法基于 Mongo 计数细化；改动面大、收益低。

理由：选项 A 在最早的同步点止血，路径短、易测、零额外线程。

### 2. 并发计数：`SlurmJobRepository.countByStatus(SlurmJobStatus.Running)`

- 使用 Spring Data 派生方法 `long countByStatus(SlurmJobStatus status)`，由 Mongo `count` 实现，索引：`slurm_job` 集合上已经被 `findByStatusAndExpiration*` 使用 `status` 字段索引。
- 计数语义："本系统认为还在跑的 Slurm 作业数"。提交时已写入 Mongo 之前的窗口不计入，但因为提交在主流程同步路径上、写 Mongo 也在 `submitSlurmJob` 内连续执行，并发 race 最多导致**短暂略微超过阈值**——这是可接受的（限流目标本来就是软上限，且不会击穿 Slurm）。
- 不引入分布式锁；Mongo 计数已经覆盖跨节点视角，因为所有节点共享同一 `slurm_job` collection。

### 3. 异常类型：`SlurmOverloadedException extends RuntimeException implements IRetry`

- 放在 `com.kiwi.bpmn.component.slurm` 包内，与 Handler 同包，便于直接抛出。
- 实现 `com.kiwi.bpmn.core.retry.IRetry` 并覆盖 `decreaseRetries()` 返回 `false`，由 `ExternalTaskRetryPlanner` 通过接口方法分发——不再需要 FQCN 字符串匹配。模块依赖：`kiwi-bpmn-component` 已经依赖 `kiwi-bpmn-core`，无需新增。
- 异常 message 形如 `"Slurm overloaded: running=53, max=50"`，便于 Camunda incident 与日志直接观察。

### 4. 重试语义抽象：`IRetry.decreaseRetries()`

- 在 `IRetry` 接口上新增 `default boolean decreaseRetries() { return true; }`：默认 `true`，与 Camunda `DefaultJobRetryCmd` 行为一致；实现方覆盖为 `false` 即可表达"本次失败不消耗 retries"，由规划器统一处理。
- 该方法对所有 `IRetry` 实现类生效——`SlurmOverloadedException` 只是首个使用者；未来其他"瞬时拥塞/资源不足"语义异常可同样实现 `IRetry` 并覆盖该方法，零修改即可获得相同处理。
- `JobRetryFailureSupport` 新增 `findIRetryOnChain(Throwable)`：沿因果链定位首个 `IRetry` 实例，供规划器读取元信息（语义与已有 `isIRetryOnChain` 对称）。

### 5. 退避策略：通用配置 `kiwi.bpm.external-task-retry.non-decreasing-retry-cycle`

- 默认 `R5/PT30S`：最多 5 次回退、每次 30 秒。该 `retries` 仅用于 cycle 表达式语义，运行时实际不消耗业务 `retries`。
- 在 `ExternalTaskRetryPlanner.plan(task, failure)` 中，于 OLE 分支之后插入"非递减重试"分支：
  - 通过 `JobRetryFailureSupport.findIRetryOnChain(f)` 取到 `IRetry` 实例；
  - 若 `!decreaseRetries()`：优先用配置的 `nonDecreasingRetryCycle`；为空时回退到现有 `resolveCycle(task)`（BPMN 节点配置 / 引擎默认 cycle）的第一个间隔；
  - `nextRetries = currentRetries`（首次为 `null` 时取 cycle 的 `retries` 值），即**不消耗 retries**。
- 该策略与 OLE 分支语义对称（都"保留 retries"），但触发条件来自异常侧的接口契约，而非外部 FQCN 配置——避免了"配置漂移"风险。

### 6. 模块依赖

- 全部新增类型均位于现有模块依赖图内：`IRetry` / `JobRetryFailureSupport` 已在 `kiwi-bpmn-core`；`kiwi-bpmn-external-task` 与 `kiwi-bpmn-component` 都依赖 `kiwi-bpmn-core`。
- `ExternalTaskRetryPlanner` 通过接口方法分发，**不**反向依赖 `kiwi-bpmn-component`；`SlurmOverloadedException` **不**反向依赖 `kiwi-bpmn-external-task`。

### 7. 日志与可观测性

- 闸门触发时输出 `WARN`，字段：`runningCount`、`maxConcurrentJobs`、`processInstanceId`、`activityId`、`externalTaskId`、`businessKey`。
- 不引入新的 metric 端点；如后续接入 Micrometer，可在 Handler 内再加 counter，本变更不引入新依赖。

## Risks / Trade-offs

- [Mongo 计数与真实 Slurm 状态可能不一致] → 跟踪窗口 `expiration` 设计上已有兜底；过载阈值是"软上限"，允许小幅漂移；本变更不引入 squeue/sacct 兜底以避免每次提交都触发外部命令。
- [`SlurmJobRepository.countByStatus` 在大集合下成本] → `status` 字段已被现有查询索引利用；记录数级别（数千到数万）下 `count` 仍是毫秒级。若未来出现热点，可改为 Mongo 维护的 atomic counter 或缓存近似值（非本次范围）。
- [retries 不消耗 → 极端持续过载可能导致 Camunda 频繁回拉] → `R5/PT30S` 实际上把"无限回拉"收敛到一个最小退避；运维侧通过日志中的 `Slurm overloaded` 频次告警观察压力。
- [`IRetry.decreaseRetries()` 是默认方法] → 既有所有 `IRetry` 实现类（如 `JobRetryException`）保持原行为；只有显式覆盖的子类（如 `SlurmOverloadedException`）才进入新分支。无既有用例被打破。
- [启用后旧的部署若未设置 properties] → `maxConcurrentJobs <= 0` 时直接跳过闸门，等价于禁用；`nonDecreasingRetryCycle` 默认 `R5/PT30S`，未设置时直接生效；不需要任何额外配置。

## Migration Plan

1. 合入代码后默认 `maxConcurrentJobs = 50`（即默认启用限流）。如线上希望先观察再启用，可在 `application.yml` 设置 `kiwi.bpm.slurm.max-concurrent-jobs: 0`。
2. 升级一台节点 → 观察 `WARN Slurm overloaded` 日志频率与 `slurm_job{status:Running}` 曲线 → 视实际峰值调阈值。
3. 回滚：仅需把配置改回 `0` 即可关闭闸门；或回退 jar 至上一版本，外部任务 retry 路径上对未识别异常的处理保持不变。

## Open Questions

- 是否需要把"过载回退"事件落库（如新增 `slurm_overload_event`），以便后续做拒答曲线分析？当前结论：先用日志观测，不落库；若运维有需求再单开 change。
- 是否对 `maxConcurrentJobs` 做"按 partition 拆分"？当前结论：不做；如需要，未来用 `Map<String, Integer>` 配置扩展，向后兼容。
