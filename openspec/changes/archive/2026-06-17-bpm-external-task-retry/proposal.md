## Why

Camunda External Task 的 retries 默认不为 BPMN 写入数值，失败时需 Worker 在 `handleFailure` 中自行传入剩余次数与间隔；团队已有的 **`failedJobRetryTimeCycle`（ISO 8601）** 与异步 Job 语义可对齐，但 External Task 侧缺少统一的默认策略与「仅对部分异常自动重试」的契约，容易导致各 Worker 重复实现、与 **`IRetry` / `JobRetryExceptionClassifier`** 等上下文不一致。需要在平台层定义可复用的默认重试配置，并允许按异常分流与按任务覆写，先由**后端与公共库**落能力，再逐步扩展客户端。

## What Changes

- 引入 **External Task 重试策略** 的**默认配置**：与引擎/全局的 **`failedJobRetryTimeCycle` 语义复用**（同 ISO 8601 表达：次数 + 间隔），在首次/后续失败时由统一组件计算并调用 `handleFailure` 的 `retries` 与 `retryTimeout`（或等效 API），避免各 topic 手算。
- 当 External Task 执行出现 **特定异常** 时，按与现有 Job 侧一致的规则识别（例如 **cause 链上的 `IRetry`、可插拔分类器**），**仅在此类「可重试」失败** 时走上述周期；非可重试失败可立即将剩余次数置 0 或走 BPMN 错误（与现设计一致，在 design 中定稿）。
- **按 External Task 实例或定义覆写**重试配置：支持独立「重试时间轴」（仍建议同一套 ISO 8601 解析），**本阶段仅后端**提供配置模型、API 或部署/流程元数据存储与解析；Worker 可继续用统一封装消费默认与覆写结果。
- 不修改 Camunda 引擎对外 SQL 中 `ACT_RU_EXT_TASK` 的「retries 初值 null」语义，而是在**平台封装层**在「第一次失败」或「配置注入点」将显式次数与下次可拉取时间写回引擎。

## Capabilities

### New Capabilities

- `bpm-external-task-retry`: 定义 External Task 默认/覆写重试配置、与 `failedJobRetryTimeCycle` 对齐的解析、以及基于异常（含 `IRetry` 与分类器）的自动重试行为需求；本阶段实现范围以**后端与共享库**为主。

### Modified Capabilities

- （无）现有 `openspec/specs/` 下无 BPM 相关能力需修改；不强制修改 `slurm-workdir-cleanup`。

## Impact

- **kiwi-bpmn**（`kiwi-bpmn-external-task`、与 External Task 客户端/本地 Worker 装配相关代码）
- **kiwi-admin backend**（若配置经 REST/DB 或流程定义扩展下发）
- **配置**：全引擎 `failedJobRetryTimeCycle` / 环境变量（已存在项）的**读取与复用**；新增 external-task 专用配置项在 design 中明确
- **依赖**：Camunda `ExternalTaskService#handleFailure`、`getRetries`；不依赖修改 Camunda 引擎源码
