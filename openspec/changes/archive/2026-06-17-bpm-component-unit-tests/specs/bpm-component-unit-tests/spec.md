## ADDED Requirements

### Requirement: Shell 组件单元测试

系统 SHALL 在 `kiwi-bpmn-component` 提供 `ShellActivityBehaviorTest`，验证命令执行成功时写入 `result` 与 `errorCode`，且缺少 `command` 时抛出 `IllegalArgumentException`。

#### Scenario: echo 命令成功

- **WHEN** 流程变量 `command` 为平台 echo 命令且 `waitFlag` 默认为 true
- **THEN** `result` SHALL 含命令输出文本，且 `errorCode` SHALL 为 `"0"`

#### Scenario: 缺少 command

- **WHEN** 未提供 `command` 流程变量
- **THEN** 执行 SHALL 抛出 `IllegalArgumentException`

### Requirement: File 读/写组件单元测试

系统 SHALL 提供 `FileReadActivityTest` 与 `FileWriteActivityTest`，覆盖读写往返、append、maxBytes 超限、非法路径（含 `..`）及文件不存在场景。

#### Scenario: 写入后读取内容一致

- **WHEN** `FileWriteActivity` 写入 UTF-8 文本后 `FileReadActivity` 读取同一路径
- **THEN** 读取的 `content` 变量 SHALL 等于写入内容

#### Scenario: 拒绝不安全路径

- **WHEN** `path` 含 `..` 路径段
- **THEN** 执行 SHALL 抛出 `IllegalArgumentException`

### Requirement: Mongo 组件单元测试

系统 SHALL 提供 `MongoActivityTest`，在 mock `MongoTemplate` 下验证 findOne、insert、count 及非法 filter JSON 失败路径。

#### Scenario: findOne 写入结果

- **WHEN** `operation=findOne` 且 mock 返回 Document
- **THEN** 结果变量 SHALL 设为该 Document 并调用 `leave`

#### Scenario: 非法 filter JSON

- **WHEN** `filter` 不是合法 JSON
- **THEN** 执行 SHALL 抛出 `IllegalArgumentException`

### Requirement: BPM 模块测试在 CI 中执行

新增测试 SHALL 纳入 `mvn -pl kiwi-bpmn/kiwi-bpmn-component,kiwi-bpmn/kiwi-bpmn-core,kiwi-bpmn/kiwi-bpmn-external-task -am test`，与现有 `bpmn-test` CI job 一致。

#### Scenario: 本地与 CI 通过

- **WHEN** 运行上述 Maven test 命令
- **THEN** 所有新增测试类 SHALL 通过
