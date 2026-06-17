## ADDED Requirements

### Requirement: Movie 流程节点可由 JavaDelegate 执行原 Handler 语义
系统 MUST 在 `cryoems-bpm` 中提供 movie 场景的 `JavaDelegate` 节点实现，使流程节点能够承接原有 handler 的核心步骤能力，并输出可用于流程分支判断的执行结果。

#### Scenario: Delegate 成功执行步骤
- **WHEN** 流程实例进入已迁移的 movie 处理节点且输入变量有效
- **THEN** 对应 `JavaDelegate` SHALL 完成步骤执行并写出成功状态变量供后续节点消费

#### Scenario: Delegate 执行失败时输出标准错误
- **WHEN** `JavaDelegate` 在步骤执行过程中出现业务异常或系统异常
- **THEN** 系统 SHALL 写出标准化错误变量（含错误信息与可重试语义）并进入流程定义的失败路径

### Requirement: JavaDelegate 输出必须采用平铺流程变量
系统 MUST 在 movie `JavaDelegate` 执行后将结果平铺写入流程输出变量，不得继续以 `StepResult` 对象作为流程结果载体。

#### Scenario: 成功结果平铺写入输出变量
- **WHEN** `JavaDelegate` 成功完成步骤处理
- **THEN** 系统 SHALL 至少写出成功标识、步骤消息及必要业务数据到平铺输出变量

#### Scenario: 失败结果平铺写入输出变量
- **WHEN** `JavaDelegate` 处理失败或捕获异常
- **THEN** 系统 SHALL 写出错误码、错误信息、可重试语义及致命标识等平铺输出变量供流程分支判断

#### Scenario: 禁止输出 StepResult 对象
- **WHEN** 迁移后的流程节点完成执行
- **THEN** 系统 SHALL NOT 在流程上下文中以 `StepResult` 作为步骤结果对象进行传递

#### Scenario: MotionCor2 与 CTFFIND5 输出批处理命令结果
- **WHEN** 流程执行到 `MotionCor2` 或 `CTFFIND5` 对应的 JavaDelegate 节点
- **THEN** 节点 SHALL 输出批处理命令字符串及关键产物路径（至少包含命令、主输出文件、日志文件），而不是仅输出决策占位字段

#### Scenario: MotionCor2 与 CTFFIND5 JavaDelegate 直接提交 Slurm 任务
- **WHEN** `MotionCor2` 或 `CTFFIND5` JavaDelegate 完成命令拼接
- **THEN** 节点 SHALL 继承 `AbstractExternalTaskHandler` 并直接调用 `submitSlurmJob` 提交 Slurm 任务，而不是仅输出待调度参数

### Requirement: Delegate 公共操作必须统一抽取复用
系统 MUST 将多个 movie `JavaDelegate` 共享的公共操作（如上下文变量读取校验、统一日志、错误包装、流程结果变量写入）抽取到抽象父类或独立 service，避免在各 delegate 中重复实现。

#### Scenario: 新增 delegate 复用公共能力
- **WHEN** 新增一个 movie 步骤对应的 `JavaDelegate`
- **THEN** 该实现 SHALL 复用公共父类或 service 提供的通用能力，仅实现步骤特有逻辑

#### Scenario: 公共行为在 delegate 间保持一致
- **WHEN** 不同 movie delegate 执行相同类型的前置校验或异常处理
- **THEN** 系统 SHALL 通过统一抽取的实现保证行为一致且可测试

### Requirement: Movie Delegate 的 ComponentDescription 元数据应保持精简一致
系统 MUST 为 movie `JavaDelegate` 添加 `@ComponentDescription`，并遵循统一约束：对由总入口统一注入的 `movie/task/taskDataset` 不在组件 `inputs` 中重复声明；`outputs` 的 `ComponentParameter.key` 使用业务字段名（如 `headerFilePath`），不使用 `movieStep.data.` 前缀；`description` 仅描述该类功能。

#### Scenario: 入口变量不在 inputs 重复声明
- **WHEN** movie delegate 的输入变量由流程总入口统一注入
- **THEN** 该 delegate 的 `@ComponentDescription` SHALL NOT 重复声明 `movie/task/taskDataset` 到 `inputs`

#### Scenario: outputs key 使用业务字段名
- **WHEN** delegate 需要声明输出参数元数据
- **THEN** 每个 `ComponentParameter.key` SHALL 使用无前缀业务键名，映射目标变量可通过注解默认值或配置表达

#### Scenario: 注解描述只体现类功能
- **WHEN** 为 delegate 填写 `@ComponentDescription.description`
- **THEN** 描述 SHALL 聚焦当前类职责，不包含实现迁移过程说明

### Requirement: 使用 ComponentParameter 的模块必须依赖 kiwi-bpmn-core
系统 MUST 在使用 `@ComponentDescription/@ComponentParameter` 的模块中显式依赖 `kiwi-bpmn-core`，确保注解与元数据模型可用。

#### Scenario: cryoems-bpm 使用注解时具备依赖
- **WHEN** `cryoems-bpm` 中存在使用 `@ComponentParameter` 的 delegate 类
- **THEN** `cryoems-bpm` 模块 SHALL 显式声明 `kiwi-bpmn-core` 依赖并可成功编译

### Requirement: kiwi-admin MUST 依赖 cyroems-bpm 并承担流程执行职责
系统 MUST 由 `kiwi-admin` 作为 movie 流程的最终执行入口，并通过模块依赖引入 `cyroems-bpm` 能力完成 `JavaDelegate` 执行与装配。

#### Scenario: kiwi-admin 完成依赖装配后可执行 delegate
- **WHEN** `kiwi-admin` 引入 `cyroems-bpm` 并加载 movie 相关流程定义
- **THEN** 流程节点 SHALL 能在 `kiwi-admin` 运行时成功定位并执行对应 `JavaDelegate`

#### Scenario: 缺失依赖时系统给出可诊断失败
- **WHEN** `kiwi-admin` 未引入 `cyroems-bpm` 或版本不兼容
- **THEN** 系统 SHALL 在启动或流程执行早期抛出可诊断错误，避免静默降级到未知行为

### Requirement: Movie 流程输入变量契约与 MovieKiwiWorkflowService 对齐
系统 MUST 将 `MovieKiwiWorkflowService` 组装的流程变量作为 delegate 输入契约，至少包含 `movie`、`task`，并支持可选 `taskDataset` 变量。

#### Scenario: 启动流程时包含基础变量
- **WHEN** `MovieKiwiWorkflowService` 发起 movie 流程实例
- **THEN** 流程上下文 SHALL 包含 `movie` 与 `task` 变量，且 delegate 可直接读取

#### Scenario: taskDataset 可选输入
- **WHEN** 调用方未提供 `taskDataset` 且服务未能加载到数据集
- **THEN** delegate SHALL 按约定的缺省分支处理，而不是因缺少该变量直接崩溃

### Requirement: 迁移期间支持步骤级兼容与回退
系统 MUST 支持 movie 步骤的增量迁移；已迁移步骤走 delegate，未迁移步骤保持兼容执行，并具备可回退机制。

#### Scenario: 未迁移步骤继续走兼容路径
- **WHEN** 流程执行到尚未迁移为 delegate 的步骤
- **THEN** 系统 SHALL 使用既有兼容执行路径，保证任务可继续推进

#### Scenario: 启用回退后停用 delegate 路径
- **WHEN** 运维或配置触发回退策略
- **THEN** 系统 SHALL 停用对应 delegate 节点实现并恢复到兼容执行路径
