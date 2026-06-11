## ADDED Requirements

### Requirement: JSON 映射组件注册

系统 SHALL 提供 Spring Bean `jsonMapActivity`，`@ComponentDescription`（group=`通用`），输入 `source`（必填）与 `mappings`（`htmlType=assignments-editor`）。

### Requirement: JSON Pointer 映射写入

执行时系统 SHALL 从 `source` 流程变量解析 JSON 根节点，按 `mappings` 中每项的 `key`（目标变量）与 `value`（JSON Pointer）提取值并写入流程变量；MissingNode 或非法输入 SHALL 失败。

#### Scenario: 从 HTTP 响应体提取字段

- **WHEN** `source` 为 `{"data":{"id":"abc"}}` 且 mappings 含 `{"key":"task_id","value":"/data/id"}`
- **THEN** 流程变量 `task_id` SHALL 为 `"abc"`

#### Scenario: 数组下标

- **WHEN** source 含 `items[0].name` 且 pointer 为 `/items/0/name`
- **THEN** 目标变量 SHALL 为对应字符串值
