## Context

- Camunda External Task 创建时 **`retries` 常为 `null`**，`areRetriesLeft()` 在 null 时仍视为可拉取；真正写入数值发生在 **`handleFailure`** / **`setRetries`**。
- 仓库已有：**全局 `failedJobRetryTimeCycle`**（`application.yml`）、**Job 侧** `FailedJobCommandFactory` 包装与 **`JobRetryExceptionClassifier`**、**`IRetry` / `RetryException`**（cause 链识别）。
- External Task Worker（External Task Client）分散在各模块时，重复实现「递减 retries + 间隔」易偏离引擎语义与本项目的 **`DefaultJobRetryExceptionClassifier`** 对齐策略。

## Goals / Non-Goals

**Goals:**

- 提供 **统一的 External Task 失败处理路径**：对「应自动重试」的失败，按 **与 `failedJobRetryTimeCycle` 相同的 ISO 8601 语义** 计算下一次 **`retries`** 与 **`retryTimeout`**（毫秒）；对「不应自动重试」的失败，**一次性耗尽**剩余 retries（或与 BPMN 错误上报路径一致，见决策）。
- **默认策略**：读取与流程引擎一致的 **`failedJobRetryTimeCycle` 配置字符串**（同源 YAML / 环境变量），避免 External Task 单独维护一套互不兼容的数字。
- **异常分流**：复用 **`JobRetryFailureSupport.isIRetryOnChain`**（或与 **`JobRetryExceptionClassifier`** 组合：**仅当分类器认为可走标准「消耗一次重试机会」时** 才应用间隔策略；否则立即耗尽）。默认可与 **`DefaultJobRetryExceptionClassifier`** 对齐（仅 `IRetry` 链上视为可走标准重试）。
- **覆写**：允许按 **流程定义 + activityId（或 BPMN 中的 external task 节点）** 配置独立 **`camunda:failedJobRetryTimeCycle` 等价字符串**（或同一 ISO 字段名）；**本阶段仅后端**：配置存储 + 解析 API 或启动时注入，Worker 侧仅消费封装结果。

**Non-Goals:**

- 修改 Camunda 引擎表结构或 Fork 引擎；不实现 Camunda Cockpit 插件。
- 本变更 **不要求** 完成前端建模 UI；不要求所有语言的外部 Worker 重写，仅需 Java 侧统一封装可达。
- 不在此设计强制「成功路径也初始化 retries 数字」；可选优化留待后续。

## Decisions

1. **时间轴来源：复用 ISO 8601 字符串，与 Job 对齐**  
   **决策**：解析逻辑与引擎 **`FailedJobRetryConfiguration`** 一致：同一字符串格式（如 `R5/PT1M` 或逗号分隔间隔列表）。实现上优先调用引擎已有 **`ParseUtil.parseRetryIntervals`** / **`DurationHelper`**（与 `DefaultJobRetryCmd` 同源），避免重复解析。  
   **备选**：自写 ISO 解析 — 拒绝，双份语义易漂移。

2. **默认配置来源**  
   **决策**：默认 External Task 重试配置 **等于** Spring 配置中 **`failedJobRetryTimeCycle`**（与 `ProcessEngineConfigurationImpl` 一致）；若未来需要单独键，可增加 **`kiwi.bpm.external-task-retry-time-cycle`**，缺省时回退到 **`failedJobRetryTimeCycle`**。  
   **理由**：用户明确要求「重用」该配置。

3. **第几次失败对应列表中第几个间隔**  
   **决策**：对齐 **`DefaultJobRetryCmd`**：`job.getRetries()` 与 **`intervalsCount`** 推导 **`indexOfInterval`**（见引擎实现），在封装层根据 **当前 External Task `getRetries()`** 与 **首次失败是否需 `initializeRetries`** 做同等语义。若当前为 `null`，在第一次可重试失败时按解析结果 **初始化**为配置中的总步数（与 Job 的 `initializeRetries` 行为对齐，具体以引擎 `FailedJobRetryConfiguration.getRetries()` 为准）。  
   **备选**：null 时固定为 3 — 拒绝，与全局 cycle 不一致。

4. **不可重试失败时的行为**  
   **决策**：调用与现有 **立刻耗尽** 一致的路径：等价于 **`retries=0` 的 `handleFailure`**（或先 **`setRetries(0)`**），以产生 incident，便于运维对齐 Job 侧「非重试」体验。  
   **备选**：`handleBpmnError` — 仅在业务显式抛出 BPMN 错误时由调用方使用，不作为分类器默认路径。

5. **覆写配置存放（后端先行）**  
   **决策（可迭代）**：**阶段 1** 使用 **Spring `@ConfigurationProperties` 或 YAML map**（如 `kiwi.bpm.external-task-retry-overrides.<logical-key>: R3/PT10S`），key 为 **`processDefinitionKey + ":" + activityId`**；后续可迁到 DB 与 REST。**阶段 1 交付**：能覆盖「独立重试配置」的配置模型 + 读取优先级：**节点覆写 > 默认**。  
   **备选**：仅存 DB — 延后，避免本阶段绑定 schema 迁移。

6. **集成点**  
   **决策**：在 **`ExternalTaskHandler`** 执行体外套 **Kiwi 提供的装饰器/基类**（或 AOP 仅对标注 bean），在 `try/catch` 中委托 **`ExternalTaskRetryExecutor`**（新组件）完成 `handleFailure` 或 `complete`。与现有 `@ExternalTaskSubscription` 注册方式兼容。

## Risks / Trade-offs

- **[Risk] 与 Camunda 内部类耦合（ParseUtil、DurationHelper）** → **缓解**：集中封装在单模块，升级 Camunda 时单点回归；可选抽接口 + 集成测试。  
- **[Risk] `getRetries()` 为 null 时与 Job 的「首次执行」判断不完全同构** → **缓解**：在 design 实现阶段对照 **`DefaultJobRetryCmd#isFirstJobExecution`** 为 External Task 建立显式状态机（用 `errorMessage` / 专用标记或仅依赖 retries null，与引擎表字段一致）。  
- **[Trade-off] 覆写仅 YAML** → 多环境需文件或配置中心；后续 DB 迁移在 tasks 中跟踪。

## Migration Plan

- 新能力 **默认关闭** 或 **装饰器显式启用**（`@ConditionalOnProperty`），避免未改动的 Handler 行为突变。  
- 回滚：关闭开关 + 恢复直连接口调用。  
- 无强制数据迁移（阶段 1 无新表）。

## Open Questions

- 覆写 key 是否需 **tenant** 维度（多租户下 `processDefinitionKey` 可能重复）— 留待产品确认；设计预留 `tenantId` 可选段。  
- 是否将 **`JobRetryExceptionClassifier`** 与 External Task **完全共用同一 Bean** — 推荐是，但需确认非 `IRetry` 技术异常在 External 侧是否也走「可重试」；可在配置中 **单独** `kiwi.bpm.external-task-retry-classifier-bean` 或默认共用。
