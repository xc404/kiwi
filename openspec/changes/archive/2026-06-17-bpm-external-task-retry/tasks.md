## 1. 重试时间轴与配置

- [x] 1.1 `ExternalTaskRetryProperties`（`kiwi.bpm.external-task-retry`）：默认周期回退 `operaton.bpm.generic-properties.properties.failedJobRetryTimeCycle`
- [x] 1.2 `ExternalTaskRetryPlanner` 内复用 Operaton `ParseUtil` + `DurationHelper` + `FailedJobRetryConfiguration`（对齐 Job 索引规则）
- [x] 1.3 输出 `com.kiwi.bpmn.core.retry.RetryPlan`（`nextRetries` / `retryTimeoutMs`）

## 2. 覆写（BPMN）

- [x] 2.1 活动上 `camunda:failedJobRetryTimeCycle`（`OperatonFailedJobRetryTimeCycle`）
- [x] 2.2 `ExternalTaskRetryCycleResolver`：`processDefinitionId` + `activityId` 解析，无则回退全局默认

## 3. 异常分类与执行器

- [x] 3.1 `ExternalTaskRetryPlanner.plan(task, failure)`：`JobRetryExceptionClassifier`；不可重试 → `retries=0`
- [x] 3.2 可重试时 BPMN 覆写 > 引擎默认 cycle，经 `handleFailure` 提交
- [x] 3.3 `RetryPlannerTest`：IRetry 递减/非递减、R5/PT1M 步进

## 4. Worker 集成

- [x] 4.1 `AbstractExternalTaskHandler` 失败路径委托 `ExternalTaskRetryPlanner`
- [x] 4.2 `ExternalTaskRetryAutoConfiguration` + `@ConditionalOnProperty(kiwi.bpm.external-task-retry.enabled)`

## 5. 验证与文档

- [x] 5.1 `RetryPlannerTest` 单元测试（无 Testcontainers 集成测；与 Job 侧重试语义对齐由 planner 单测覆盖）
- [x] 5.2 `application.yml` 注释说明开关、default-time-cycle、non-decreasing-retry-cycle
