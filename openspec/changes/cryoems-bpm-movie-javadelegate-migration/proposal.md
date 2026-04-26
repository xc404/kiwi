## Why

当前 movie 处理仍保留大量本地 Handler 逻辑，虽然已有调用流程能力，但执行语义分散在旧引擎与新流程之间，难以统一维护与演进。现在需要把关键处理步骤迁移到 `cryoems-bpm` 的 `JavaDelegate`，让流程引擎成为唯一编排入口，并以 `MovieKiwiWorkflowService` 已有入参作为稳定契约。

## What Changes

- 在 `cryoems-bpm` 中新增可复用的 movie 处理 `JavaDelegate`，承接原 Handler 的核心行为。
- 明确运行边界：流程最终由 `kiwi-admin` 执行，`kiwi-admin` 需要依赖 `cyroems-bpm` 提供的 delegate/流程能力。
- 约束并文档化 movie 流程入参（至少包含 `movie`、`task`、可选 `taskDataset`），与 `MovieKiwiWorkflowService` 对齐。
- 明确结果表达方式：迁移到 `JavaDelegate` 后不再使用 `StepResult` 对象，执行结果改为平铺写入流程输出变量。
- 调整 movie 流程定义与调用约定，使处理步骤由 BPM 节点驱动，不再依赖“保留旧 handler 才能工作”的隐含前提。
- 为迁移阶段提供兼容策略：未迁移步骤可继续走现有逻辑，已迁移步骤以 delegate 为准。

## Capabilities

### New Capabilities
- `movie-workflow-javadelegate-migration`: 在 cryoems-bpm 中通过 JavaDelegate 执行原 movie handler 的能力，并定义流程变量输入契约与迁移边界。

### Modified Capabilities
- （无）

## Impact

- 影响模块：`cryoems-bpm`、`kiwi-admin`、`cyroems`（`MovieKiwiWorkflowService` 相关调用路径与变量组装约定）。
- 影响流程：movie 相关 BPMN 节点执行实现从本地 Handler 迁移至 `JavaDelegate`。
- 影响依赖：需要在 `kiwi-admin` 中显式引入 `cyroems-bpm` 依赖并完成运行时装配。
- 影响运维与调试：步骤状态与异常需要在流程实例上下文中可观测，便于替代原有 handler 日志链路。
