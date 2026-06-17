# bpm-component-generate-save-conflict Specification

## Purpose
TBD - created by archiving change bpm-generate-component-duplicate-confirm. Update Purpose after archive.
## Requirements
### Requirement: 使用 sourceKey 标识生成来源并用于冲突判定

`BpmComponent` MUST 具备字段 **`sourceKey`**，用于表示生成来源维度的稳定标识，**不得**复用既有字段 **`key`** 承担本能力中的判重职责（`key` 保留其它业务用途）。

在通过 CLI help 或 OpenAPI/Swagger 生成 BPM 组件并准备写入组件库时，系统 MUST 在持久化前根据 **`parentId` 与 `sourceKey` 的组合** 识别是否与已有组件冲突（空值匹配规则与实现层约定一致）。冲突判定 MUST NOT 仅依据 `key` 或与 `sourceKey` 无关的字段组合替代上述规则。

#### Scenario: 生成器写入 sourceKey

- **WHEN** 系统从 CLI help 或 OpenAPI 生成组件草稿
- **THEN** 每条草稿 MUST 携带可稳定复现的 `sourceKey`
- **AND** 该值 MUST 用于后续预检与冲突处理

#### Scenario: 无冲突时直接保存

- **WHEN** 待保存的生成结果中每一条的 `(parentId, sourceKey)` 在库中均不存在
- **THEN** 系统 MUST 按现有新增语义落库（无需用户逐项确认冲突策略）

#### Scenario: 存在冲突时不得静默覆盖

- **WHEN** 待保存条目中至少有一条的 `(parentId, sourceKey)` 已在库中存在
- **THEN** 系统 MUST NOT 在未询问用户的情况下用草稿覆盖已有文档或静默跳过
- **AND** 系统 MUST 向用户展示冲突项并等待用户对每条选择后续动作

### Requirement: 用户对每条冲突可选择取消、覆盖或新增

对于每一条检测结果为冲突的生成草稿，用户 MUST 能够独立选择：**取消**（本条不保存）、**覆盖**（用草稿更新库中已存在的那条组件）、**新增**（仍插入一条新组件，且 MUST 通过调整 **`sourceKey`** 使新记录在 `(parentId, sourceKey)` 下不与已有记录冲突）。

#### Scenario: 用户选择取消

- **WHEN** 用户对某条冲突草稿选择取消
- **THEN** 该条 MUST NOT 被写入或更新到组件库

#### Scenario: 用户选择覆盖

- **WHEN** 用户对某条冲突草稿选择覆盖
- **THEN** 系统 MUST 用该草稿更新库中已存在的对应组件记录（保留同一持久化标识）

#### Scenario: 用户选择新增

- **WHEN** 用户对某条冲突草稿选择新增
- **THEN** 系统 MUST 插入一条新组件记录
- **AND** 新记录的 `(parentId, sourceKey)` MUST 与库内已有记录不冲突（例如为草稿生成新的唯一 `sourceKey`）

### Requirement: CLI 与 OpenAPI 生成入口共用冲突处理流程

从命令行 help 生成与从 OpenAPI 批量生成 MUST 复用同一套「预检冲突 → 用户确认 → 按选择落库」的行为与前端实现路径；差异仅在于待保存草稿的单条与多条数量。

#### Scenario: CLI 单条生成

- **WHEN** 用户通过 CLI help 生成并确认保存
- **THEN** 冲突检测与用户选择交互 MUST 与 OpenAPI 多条场景使用同一逻辑，仅草稿列表长度为一

#### Scenario: OpenAPI 批量生成

- **WHEN** 用户通过 OpenAPI 生成多条草稿并确认保存
- **THEN** 系统 MUST 对每条草稿独立应用冲突检测与用户选择（允许用户对不同条目选择不同动作）

