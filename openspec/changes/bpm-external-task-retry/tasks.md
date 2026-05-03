## 1. 重试时间轴与配置

- [ ] 1.1 定义 `ExternalTaskRetrySettings` / 配置属性：默认周期字符串（回退到与引擎相同的 `failedJobRetryTimeCycle` 键）、可选 `kiwi.bpm.external-task-retry-time-cycle` 回退链
- [ ] 1.2 实现 `FailedJobRetryTimeCycleParser`（或复用 Camunda `ParseUtil` + `DurationHelper`）将 cycle 解析为 **间隔列表 + 总步数**，与 `DefaultJobRetryCmd` 索引规则对齐
- [ ] 1.3 实现 `ExternalTaskRetryPlan`：输入（当前 `getRetries()`、是否首次失败、cycle 字符串），输出（下一 `retries`、`retryTimeout` 毫秒）

## 2. 覆写（BPMN）

- [x] 2.1 在活动上配置 **`camunda:failedJobRetryTimeCycle`**（与 Job 相同扩展元素）；解析 key 为 **`processDefinitionId` + `activityId`**
- [x] 2.2 实现 `ExternalTaskRetryCycleResolver`（`RepositoryService#getBpmnModelInstance`）：读取节点 extension，无则回退全局默认

## 3. 异常分类与执行器

- [ ] 3.1 实现 `ExternalTaskRetryExecutor`：依赖 `JobRetryExceptionClassifier`（可注入），不可重试时调用 **耗尽 retries** 的 `handleFailure` 路径
- [ ] 3.2 可重试时根据 `ExternalTaskRetryPlan` 与 **覆写 > 默认** 的 cycle 调用 `handleFailure`（或组合 `setRetries` + 锁/超时，以 Camunda 推荐 API 为准）
- [ ] 3.3 为 `IRetry` / cause 链增加单测：与 `JobRetryFailureSupport` 行为一致

## 4. Worker 集成

- [ ] 4.1 提供 `ExternalTaskHandler` 装饰器或抽象基类，在 `try/catch` 中委托 `ExternalTaskRetryExecutor`，成功路径仍 `complete`
- [ ] 4.2 增加 `@ConditionalOnProperty`（如 `kiwi.bpm.external-task-retry-enabled`）便于渐进 rollout

## 5. 验证与文档

- [ ] 5.1 编写集成测试或 Testcontainers：模拟 external task 失败，`handleFailure` 被传入预期 `retries`/`retryTimeout`
- [ ] 5.2 在 `application.yml` / README 片段说明默认键、覆写 map、开关；归档前核对 `openspec` tasks 勾选
