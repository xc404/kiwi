## ADDED Requirements

### Requirement: 已认证用户可获取监控快照

系统 SHALL 向已登录用户提供 `GET /monitor/snapshot` 接口，返回 JSON 体，包含采集时间戳与若干监控模块；每个模块包含稳定标识 `id`、展示标题 `title`、排序序号 `order` 与指标列表。

#### Scenario: 登录用户拉取快照

- **WHEN** 已登录客户端请求 `GET /monitor/snapshot`
- **THEN** 响应体包含 `collectedAt` 与 `modules` 数组，且各模块包含 `metrics` 数组

### Requirement: 监控模块可后端扩展

系统 SHALL 通过实现 `MonitorContributor` 并注册为 Spring Bean 的方式增加新的监控模块；聚合器 SHALL 按 `order` 升序合并各模块指标。

#### Scenario: 新增贡献者出现在快照中

- **WHEN** 新增一个 `MonitorContributor` Bean 且 `collect()` 返回非空指标列表
- **THEN** 快照 `modules` 中出现对应 `moduleId` 与指标

### Requirement: 单模块采集失败隔离

若某一 `MonitorContributor` 在采集时抛出异常，系统 SHALL 仍在该模块内返回可读错误信息，且其他模块指标 SHALL 正常返回。

#### Scenario: 单模块异常

- **WHEN** 某一贡献者在 `collect()` 中抛出运行时异常
- **THEN** 该模块 `metrics` 包含标识为采集失败的文本指标，且 HTTP 响应仍为成功业务封装

### Requirement: 前端按指标类型渲染

监控页 SHALL 根据每个指标的 `kind` 字段选择展示方式（至少支持 percent、number、bytes、boolean、text）；并 SHALL 支持定时刷新与手动刷新。

#### Scenario: 展示百分比类指标

- **WHEN** 某指标 `kind` 为 `percent` 且 `value` 为 0–100 范围内数值
- **THEN** 页面以进度条或等价可视化展示该占用比例
