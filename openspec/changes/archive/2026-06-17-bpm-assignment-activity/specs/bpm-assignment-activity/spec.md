## ADDED Requirements

### Requirement: 赋值组件注册为 Spring Bean Activity

系统 SHALL 提供名为 `assignmentActivity` 的 Spring 组件类，该类 MUST 继承 Camunda `AbstractBpmnActivityBehavior`，MUST 使用 `@ComponentDescription` 暴露为 BPM 组件（类型为 Spring Bean，与其它 Activity 一致），且 MUST 通过现有组件扫描与部署机制出现在可配置的服务任务组件列表中。

#### Scenario: 组件元数据包含赋值输入

- **WHEN** 读取该组件的 `ComponentDescription` 元数据
- **THEN** 其输入参数 SHALL 至少包含键为 `assignments` 的参数，且 SHALL 用于承载 JSON 对象字符串（批量变量赋值定义）

### Requirement: 按 JSON 与变量引用写入流程变量

当服务任务执行该组件时，系统 SHALL 从流程变量中读取 `assignments` 对应的字符串值，并将其解析为**顶层 JSON 对象**。对每一个键值对，系统 SHALL：

- 若值为字符串且**整体**匹配 `${varName}` 形式（`varName` 为标识符），则 SHALL 将流程变量 `varName` 的当前值写入目标变量（键名）；
- 否则 SHALL 将解析得到的 JSON 值（数字、布尔、字符串、数组、嵌套对象等）按原生语义写入目标变量。

解析失败、非对象顶层、或 `${varName}` 引用变量不存在时，系统 SHALL 失败并产生可诊断错误（不得静默忽略）。

#### Scenario: 字面量赋值

- **WHEN** `assignments` 为 `{"x": 1, "flag": true, "msg": "hi"}`
- **THEN** 流程变量 `x`、`flag`、`msg` SHALL 分别被设置为 `1`、`true`、`"hi"`

#### Scenario: 变量引用赋值

- **WHEN** 流程变量 `a` 已存在且 `assignments` 为 `{"b": "${a}"}`
- **THEN** 流程变量 `b` SHALL 等于 `a` 的当前值

#### Scenario: 非法输入失败

- **WHEN** `assignments` 不是合法 JSON 对象字符串，或 `${name}` 中 `name` 无对应变量
- **THEN** 执行 SHALL 失败并报告错误

### Requirement: 执行后正常离开

成功写入变量后，该 Activity SHALL 按 Camunda 活动语义继续流出（`leave`），不得在无错误情况下滞留。

#### Scenario: 继续流转

- **WHEN** 赋值逻辑成功完成
- **THEN** 流程 SHALL 进入该服务任务之后的顺序流
