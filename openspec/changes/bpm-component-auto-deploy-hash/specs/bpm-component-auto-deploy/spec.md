## ADDED Requirements

### Requirement: 自动部署按部署指纹增量写库

当配置 `bpm.component.auto-deploy` 为 true 时，系统 SHALL 在从 `BpmComponentProvider` 同步组件至 MongoDB 的过程中，对每个组件计算**部署指纹**，并 SHALL 仅当该指纹与数据库中已存在同标识组件的已存指纹不一致（或不存在已存指纹）时，对该组件执行持久化更新。

部署指纹 MUST 为基于组件元数据（包含注解 `version`、名称、描述、分组、类型、输入/输出参数等影响设计器展示的字段）的稳定哈希（SHA-256 十六进制），且 MUST 持久化在组件文档上供下次启动比较。

#### Scenario: 元数据未变更时跳过写入

- **WHEN** 启动时某组件的计算部署指纹与数据库中同 `id` 文档的 `deploymentSignature` 相同
- **THEN** 系统不得对该组件执行不必要的覆盖写入（以跳过写库或等价优化实现）

#### Scenario: 新组件或指纹变化时写入

- **WHEN** 组件为首次出现，或计算指纹与已存 `deploymentSignature` 不同
- **THEN** 系统 SHALL 持久化该组件并 SHALL 更新其 `deploymentSignature`

#### Scenario: 历史数据无指纹

- **WHEN** 数据库中文档缺少 `deploymentSignature` 或为空
- **THEN** 系统 SHALL 视为需要部署并在写入后补全指纹

### Requirement: 手动部署单组件时刷新指纹

对单条组件执行 `deployComponent`（或等价保存入口）时，系统 SHALL 在保存前根据当前组件元数据计算并设置 `deploymentSignature`，以保证与自动部署使用同一套指纹规则。

#### Scenario: 手动保存后指纹一致

- **WHEN** 调用 `deployComponent` 保存某组件
- **THEN** 持久化后的文档 SHALL 带有与当前元数据一致的 `deploymentSignature`
