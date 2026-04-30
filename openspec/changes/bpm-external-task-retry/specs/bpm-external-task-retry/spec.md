## ADDED Requirements

### Requirement: 默认重试时间轴与引擎 failedJobRetryTimeCycle 一致

本能力 SHALL 使用与 Camunda 流程引擎 **`failedJobRetryTimeCycle`** 相同的 **ISO 8601 重复区间 / 间隔列表** 字符串语义，作为 External Task **未配置覆写时** 的默认自动重试时间轴。解析与间隔计算行为 SHALL 与引擎对异步 Job 的 **`DefaultJobRetryCmd` + `FailedJobRetryConfiguration`** 行为对齐（含 `R…/PT…` 与逗号分隔多段间隔的语义），不得引入第二套互斥的「仅 External Task 适用」数字规则。

#### Scenario: 使用全局默认 cycle 计算下次失败

- **WHEN** 某 External Task 未配置任何覆写，且某次失败被分类为「应消耗一次按周期重试的机会」
- **THEN** 系统 SHALL 根据当前任务 `retries` 状态与默认 `failedJobRetryTimeCycle` 字符串，计算并提交引擎可接受的 **剩余 `retries`** 与 **`retryTimeout`**（或等效 `handleFailure` 参数），使该任务在约定延迟后再次可被拉取，且与同一字符串在 Job 侧解释一致

#### Scenario: 配置同源

- **WHEN** 运维在 Spring 配置中设置与引擎相同的 `failedJobRetryTimeCycle` 值
- **THEN** External Task 默认重试策略 SHALL 能读取该值（或项目约定的单一回退键），不需要为 External Task 再维护一份不同字段名但含义冲突的「最大次数」

### Requirement: 基于异常与项目分类器的可重试判定

本能力 SHALL 在 External Task 处理失败时，根据 **失败异常链** 判断该次失败是否 **允许按默认/覆写时间轴消耗一次重试机会**。判定 SHALL 支持 **cause 链上的 `IRetry` 标记**（与现有 `JobRetryFailureSupport` 语义一致），并 SHALL 允许接入与 Job 侧 **同一套** `JobRetryExceptionClassifier` 或项目明确配置的专用分类器，使「仅特定异常自动重试」在 Job 与 External Task 间可统一。

#### Scenario: IRetry 链上失败走周期重试

- **WHEN** 失败根因或 cause 链上存在实现 `IRetry` 的异常（如 `RetryException`）
- **THEN** 该次失败 SHALL 被判定为可进入「按时间轴重试」分支（除非覆写配置或策略显式禁止）

#### Scenario: 不可重试失败立即耗尽

- **WHEN** 失败被分类为不可按周期重试
- **THEN** 系统 SHALL 通过引擎 API 使该 External Task **不再具备可拉取重试机会**（等价于将剩余重试耗尽并产生 failed external task incident 的条件与 Camunda 约定一致），且 SHALL 不应用默认间隔延迟中的「下一次执行」

### Requirement: 按流程/节点覆写重试时间轴（后端优先）

本能力 SHALL 支持为 **指定流程定义与活动（BPMN 中 external 实现的服务任务等）** 配置 **独立** 的 `failedJobRetryTimeCycle` 等价字符串。解析优先级 SHALL 为：**节点覆写 > 默认全局配置**。本阶段 SHALL 至少提供 **后端可配置载体**（例如 Spring YAML / 配置属性 map），并 SHALL 不要求前端建模工具在同一版本中交付。

#### Scenario: 覆写生效

- **WHEN** 某 external 活动已在后端配置中登记独立 cycle 字符串，且该活动实例发生可重试分类的失败
- **THEN** 间隔与剩余次数计算 SHALL 使用该覆写字符串，而非仅用全局默认

#### Scenario: 未登记覆写回退默认

- **WHEN** 某 external 活动未配置覆写
- **THEN** SHALL 仅使用默认 `failedJobRetryTimeCycle`（或项目约定的单一默认键）

### Requirement: 集成封装不改变 Camunda 引擎核心语义

本能力 SHALL 仅通过 **Camunda 公开 API**（如 `ExternalTaskService#handleFailure`、`#complete`、`#setRetries` 等）操作 External Task， SHALL NOT 要求修改 Camunda 引擎源码或数据库结构。

#### Scenario: API 边界

- **WHEN** 实现统一重试封装
- **THEN** 对外集成 SHALL 限制在上述公开 API 与 Spring 配置；引擎版本升级时以适配封装层为主
