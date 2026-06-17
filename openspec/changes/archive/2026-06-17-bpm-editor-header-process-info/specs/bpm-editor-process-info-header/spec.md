## ADDED Requirements

### Requirement: Process metadata visible at top of BPM editor

BPM 流程设计器页面 SHALL 在画布编辑区顶部展示当前流程定义的只读元信息，至少包含：**流程名称**、**最后修改时间**、**部署时间**（未部署时 SHALL 以用户可理解的方式标明，例如显示「未部署」或等价占位）。

#### Scenario: Metadata appears after load

- **WHEN** 用户通过路由进入某流程的设计页且 `getProcessById` 成功返回
- **THEN** 顶部信息区展示该流程的名称、最后修改时间与部署相关时间（或明确的未部署状态）

#### Scenario: Metadata updates after save or deploy

- **WHEN** 用户保存流程定义或部署成功且前端用返回数据更新了当前流程状态
- **THEN** 顶部信息区展示的修改时间与部署信息 SHALL 与最新数据一致

### Requirement: Graceful loading and missing fields

在流程数据尚未加载完成或部分字段缺失时，系统 SHALL 避免展示错误信息；加载中 SHALL 有明确占位，缺失字段 SHALL 使用占位符（如「—」）而非空白或 `undefined` 文案。

#### Scenario: Loading state

- **WHEN** 流程详情请求尚未完成
- **THEN** 顶部信息区显示加载中或骨架占位，不展示误导性的静态名称

#### Scenario: Optional fields absent

- **WHEN** 某可选字段（如部署时间）不存在
- **THEN** 用户仍能看到名称与修改时间（若存在），部署相关展示符合「未部署」或占位约定
