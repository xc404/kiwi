## Context

movie 场景目前存在两条执行语义：一条是旧 `Handler` 链在本进程推进，另一条是通过 `MovieKiwiWorkflowService` 启动外部流程实例。`MovieKiwiWorkflowService` 已固定了流程输入变量组装方式（`movie`、`task`、可选 `taskDataset`），但流程节点侧尚未完整承接原 handler 的业务语义，导致“流程已接入但仍依赖旧 handler”。

本次设计跨 `cyroems`、`cryoems-bpm` 与 `kiwi-admin` 三个模块，需要在不破坏现网任务执行的前提下，逐步把 movie 的处理能力迁移到 BPM `JavaDelegate`，并保持状态可观测与回滚路径明确。运行职责上由 `kiwi-admin` 作为流程最终执行方，依赖 `cyroems-bpm` 的流程能力实现。

## Goals / Non-Goals

**Goals:**
- 在 `cryoems-bpm` 中建立可复用的 movie `JavaDelegate` 执行模式，承接原 handler 的核心步骤能力。
- 建立 `kiwi-admin -> cyroems-bpm` 的清晰依赖关系，确保流程执行与 delegate 装配在 `kiwi-admin` 侧完成。
- 固化流程输入契约，确保 delegate 仅依赖 `MovieKiwiWorkflowService` 提供的流程变量。
- 将多个 movie 处理节点的公共操作进行抽取复用，避免 delegate 间重复实现。
- 提供分阶段迁移机制：可按步骤切换到 delegate，未迁移步骤保持兼容。
- 保证失败处理、日志、结果写回语义可追踪，便于替代旧 handler 链路。

**Non-Goals:**
- 不在本次一次性替换全部 movie handler 实现。
- 不重构 `MovieKiwiWorkflowService` 的启动入口与远程调用协议。
- 不引入新的流程引擎或改变现有部署拓扑。

## Decisions

### 决策 1：以流程变量契约作为唯一 delegate 输入边界
- 方案：所有新 `JavaDelegate` 只从流程上下文读取 `movie`、`task`、`taskDataset`（可空）以及流程元信息，不直接反查旧上下文对象。
- 原因：`MovieKiwiWorkflowService` 已是当前启动入口，输入契约稳定且可测试，能降低耦合。
- 备选：
  - 直接在 delegate 内部自行查询 Mongo/Repository：实现快，但会隐藏输入来源并增加副作用。
  - 继续依赖旧 Handler 上下文对象：无法真正完成迁移，技术债保留。

### 决策 2：采用“桥接 delegate”逐步替换 handler 逻辑
- 方案：先实现通用基类/工具，节点 delegate 可调用现有业务服务或抽出的步骤服务，避免直接复制旧 handler 全量代码。
- 原因：降低迁移风险，允许按步骤灰度，避免大爆炸式重写。
- 备选：
  - 一次性重写全部 handler 逻辑到 delegate：风险高，回归面过大。
  - 仅在 BPM 节点中反射调用旧 handler：迁移名义成立但边界仍不清晰。

### 决策 2.1：执行侧统一放在 kiwi-admin，能力侧沉淀在 cyroems-bpm
- 方案：`cyroems-bpm` 输出可被复用的 delegate 与流程支持能力；`kiwi-admin` 通过模块依赖引入并在其运行时装配执行。
- 原因：符合“管理端统一执行流程”的部署边界，减少多入口执行带来的一致性问题。
- 备选：
  - 在 `cyroems` 内直接执行流程：会与 `kiwi-admin` 的运行职责冲突。
  - 两侧都可执行：会造成版本漂移与排障复杂度上升。

### 决策 3：用平铺流程变量替代 StepResult 对象
- 方案：迁移到 `JavaDelegate` 后不再输出 `StepResult` 对象，统一将结果平铺为流程变量（如 `stepSuccess`、`stepCode`、`stepMessage`、`retryable`、`fatal`、`stepData.*`），并保留关键日志字段（taskId/movieId/stepKey）。
- 原因：对齐原 handler 的可观测性，同时让 BPMN 网关可基于明确变量分支。
- 备选：
  - 沿用 `StepResult` 作为对象变量：跨引擎/跨模块序列化与兼容成本高，不利于流程可视化判断。
  - 仅抛异常让引擎兜底：可观测性不足，业务分支难表达。

### 决策 4：公共逻辑优先沉淀到父类或 Service
- 方案：把 handler/delegate 共用的步骤前置校验、上下文读取、错误包装、日志打点、结果变量写出等能力抽到抽象父类或独立 service，具体 delegate 只保留步骤特有业务逻辑。
- 原因：减少重复代码，保证不同 delegate 的行为一致性，降低后续扩展成本。
- 备选：
  - 每个 delegate 独立实现公共逻辑：短期实现快，但长期维护成本高且容易行为漂移。
  - 全部放在单一工具类：复用可行但职责边界不清晰，测试粒度较差。

### 决策 5：保留兼容开关与回退路径
- 方案：通过流程定义版本或任务级配置控制“新 delegate 步骤是否启用”；异常时允许切回未迁移路径。
- 原因：movie 任务量大，必须支持增量上线与快速回退。
- 备选：
  - 强制全量切换：上线窗口和故障恢复成本过高。

## Risks / Trade-offs

- [流程变量序列化差异导致 delegate 取值失败] → 通过契约测试覆盖 `movie/task/taskDataset` 的字段可读性，并在 delegate 入口做必填校验。
- [StepResult 到平铺变量映射不完整导致语义丢失] → 制定统一变量字典与映射表，迁移每个步骤时对照验证 success/error/retry/fatal/data。
- [模块依赖版本不一致导致运行时冲突] → 统一由父工程管理 `kiwi-admin` 与 `cyroems-bpm` 版本，并在集成阶段执行依赖树检查。
- [公共逻辑抽取不当导致父类过重] → 采用“父类最小模板 + service 分层”方式，父类只保留流程骨架，复杂能力下沉 service。
- [旧 handler 与新 delegate 并存期间行为不一致] → 为每个迁移步骤建立对照用例，先对齐输入/输出语义再切流量。
- [异常语义变化影响上游调度] → 统一错误映射规范，明确“可重试/不可重试”并加回归测试。
- [迁移周期拉长导致双轨维护成本上升] → 按步骤制定完成定义（DoD），每迁完一类能力即冻结对应旧路径变更。

## Migration Plan

1. 盘点 movie 流程中优先迁移的 handler 步骤，定义步骤键与 BPM 节点映射。
2. 在 `cryoems-bpm` 新增 delegate 基础设施（上下文读取、错误映射、日志规范），并同步沉淀公共父类/公共 service。
3. 在 `kiwi-admin` 引入 `cyroems-bpm` 依赖并完成运行时装配验证。
4. 先迁移一个核心步骤（例如 header/motion/ctf 中一个）做端到端验证。
5. 在流程定义中启用该步骤 delegate，并保留任务级回退开关。
6. 扩展到其余步骤，完成后清理冗余旧 handler 依赖。

## Open Questions

- 首批优先迁移的步骤清单是否按风险（低风险先行）还是按价值（高频先行）排序？
- `taskDataset` 缺失时 delegate 的默认策略是降级执行还是直接失败？
- 回退控制粒度最终落在“流程版本级”还是“任务配置级”？
- `kiwi-admin` 到 `cyroems-bpm` 的依赖是直接模块依赖还是通过独立 starter 暴露？
