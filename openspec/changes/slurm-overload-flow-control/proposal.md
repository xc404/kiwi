## Why

Slurm 外部任务处理器（`SlurmExternalTaskHandler`）目前对每个被 fetch 到的 External Task 都立刻调用 `sbatch`，没有任何应用层的并发 / 速率门控。流程并发拉起时，可能瞬时向集群提交远超调度策略所能高效消化的作业；与此同时，本系统使用 Mongo `expiration` 跟踪窗口，长时间停留在 Slurm `PD` 状态的作业会被本地判定为超时失败上报，因此**不能简单地让 Slurm 自己排队来扛过载**。需要一层应用级闸门，把超出阈值的提交"安全地退回"到 Camunda 外部任务队列，而不是真正塞进 Slurm。

## What Changes

- 新增配置 `kiwi.bpm.slurm.max-concurrent-jobs`（默认 `50`，`<= 0` 表示不限流）。
- 在 `IRetry` 接口上新增默认方法 `decreaseRetries()`（默认 `true`），用于表达"本次失败是否消耗一次重试配额"。
- 新增 `SlurmOverloadedException`，实现 `IRetry` 并覆盖 `decreaseRetries()` 为 `false`，表达"瞬时拥塞"语义。
- `JobRetryFailureSupport` 新增 `findIRetryOnChain(Throwable)`，沿因果链定位首个 `IRetry` 实例。
- 在 `SlurmExternalTaskHandler` 提交 sbatch **之前**统计 Mongo `slurm_job` 中 `status=Running` 的条数；超过 `maxConcurrentJobs` 时抛出 `SlurmOverloadedException`，**不调用 sbatch**、**不写 Mongo 跟踪记录**。
- `ExternalTaskRetryPlanner` 通过 `IRetry.decreaseRetries()` 分发：当返回 `false` 时保留当前 `retries`，按 `kiwi.bpm.external-task-retry.non-decreasing-retry-cycle`（默认 `R5/PT30S`）计算 `retryTimeoutMs`，由 Camunda 在退避后再次释放给同 topic 的外部任务拉取。该机制对所有"非递减重试"异常通用，不仅限于 Slurm 过载。
- 日志中记录过载事件（当前计数、阈值、processInstanceId、activityId、externalTaskId），便于运维观察压力曲线。

## Capabilities

### New Capabilities
- `slurm-flow-control`: Slurm 提交链路的应用级并发闸门、过载回退与配套退避策略；以及通用的"非递减重试"接口能力（`IRetry.decreaseRetries()`）。

### Modified Capabilities
<!-- 无：External Task 的失败/重试是通用机制，本变更通过 IRetry 接口扩展能力，不改既有 spec 行为 -->

## Impact

- 代码：
  - `kiwi-bpmn/kiwi-bpmn-core/src/main/java/com/kiwi/bpmn/core/retry/IRetry.java`：新增默认方法 `decreaseRetries()`。
  - `kiwi-bpmn/kiwi-bpmn-core/src/main/java/com/kiwi/bpmn/core/retry/JobRetryFailureSupport.java`：新增 `findIRetryOnChain(Throwable)`。
  - `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmProperties.java`：新增 `maxConcurrentJobs` 字段。
  - `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmJobRepository.java`：新增按状态计数方法。
  - `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmOverloadedException.java`：新增异常类型，实现 `IRetry` 并覆盖 `decreaseRetries() = false`。
  - `kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmExternalTaskHandler.java`：注入 repository 与 properties，在提交前做闸门判断。
  - `kiwi-bpmn/kiwi-bpmn-external-task/src/main/java/com/kiwi/bpmn/external/retry/ExternalTaskRetryPlanner.java`：通过 `IRetry.decreaseRetries()` 分发非递减重试分支。
  - `kiwi-bpmn/kiwi-bpmn-external-task/src/main/java/com/kiwi/bpmn/external/config/ExternalTaskRetryProperties.java`：新增 `nonDecreasingRetryCycle` 配置（默认 `R5/PT30S`）。
- 配置：`application*.yml` 可选覆盖；不强制改动现有部署。
- 行为：现有流程不开启限流时（`<= 0`）完全等价于改动前；开启时超额任务表现为"短暂延迟再次执行"而非失败。
- 依赖：无新增第三方依赖；模块依赖方向不变（`external-task` → `core`、`component` → `core`，无新增反向依赖）。
