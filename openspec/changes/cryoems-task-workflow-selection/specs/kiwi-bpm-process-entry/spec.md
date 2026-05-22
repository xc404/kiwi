## ADDED Requirements

### Requirement: BpmProcess 入口流程标记
kiwi-admin 中的 `BpmProcess` 模型 SHALL 包含一个布尔属性 `entry`，用于标识该流程是否为可被外部下游系统选用的"入口流程"。该属性的默认值 MUST 为 `false`，以保证未显式标注的历史流程在新查询端点中默认隐藏。

#### Scenario: 新创建的流程默认非入口
- **WHEN** 用户通过 `POST /bpm/process` 新建流程定义而未传入 `entry` 字段
- **THEN** 该 `BpmProcess` 文档持久化后 `entry` 取值为 `false`

#### Scenario: 通过保存接口翻转入口标记
- **WHEN** 用户通过 `PUT /bpm/process/{id}` 提交 `entry=true`
- **THEN** 该流程的 `entry` 字段持久化为 `true`，下次 `GET /bpm/process/{id}` 与列表查询返回的对象中 `entry` 字段反映为 `true`

#### Scenario: Mongo 历史文档兼容
- **WHEN** kiwi-admin 升级前已存在的 `BpmProcess` 文档没有 `entry` 字段
- **THEN** 经由 Spring Data 反序列化为 Java 对象时 `entry` 取值 `false`，且不需要任何数据迁移脚本

### Requirement: 入口流程查询端点
kiwi-admin SHALL 暴露 `GET /bpm/process/entries` 端点，用于返回"可被下游系统选用的已部署入口流程清单"。该端点 MUST 仅返回同时满足以下三个条件的 `BpmProcess`：`entry == true` 且 `deployedVersion > 0` 且 `deployedAt != null`。该端点 MUST 接受可选查询参数 `projectId` 用于按项目过滤；若未传 `projectId` 则跨项目返回所有满足条件的流程。

#### Scenario: 仅返回已部署入口流程
- **WHEN** 客户端调用 `GET /bpm/process/entries?projectId=P1`
- **AND** 项目 `P1` 下存在三条流程 A(entry=true,deployed) / B(entry=true,undeployed) / C(entry=false,deployed)
- **THEN** 响应仅包含流程 A，不包含 B 与 C

#### Scenario: 不传 projectId 时跨项目返回
- **WHEN** 客户端调用 `GET /bpm/process/entries`（不带 `projectId`）
- **THEN** 响应返回当前 kiwi-admin 中所有 `entry=true ∧ deployedVersion>0 ∧ deployedAt!=null` 的流程

#### Scenario: 响应中保留版本信息
- **WHEN** 客户端调用 `GET /bpm/process/entries?projectId=P1`
- **THEN** 每条返回的流程对象 MUST 至少包含 `id`、`name`、`projectId`、`deployedVersion`、`deployedAt`、`entry` 字段，使下游能够区分版本

### Requirement: BPM 设计器入口流程复选框
kiwi-admin 前端 BPM 设计器的"流程属性面板"SHALL 提供一个布尔复选框允许用户切换当前流程的 `entry` 字段，并通过 `PUT /bpm/process/{id}` 持久化。该复选框 MUST 提供文案说明，告知用户勾选后该流程将出现在 cryoEMS 等下游系统的流程选择列表中。

#### Scenario: 勾选并保存
- **WHEN** 用户在流程属性面板勾选"入口流程"复选框并触发保存
- **THEN** 前端调用 `PUT /bpm/process/{id}` 携带 `entry=true`，保存成功后复选框保持选中状态

#### Scenario: 取消勾选并保存
- **WHEN** 已经是入口流程的当前流程，用户取消勾选并触发保存
- **THEN** 前端调用 `PUT /bpm/process/{id}` 携带 `entry=false`，保存成功后该流程不再出现在 `GET /bpm/process/entries` 的返回结果中
