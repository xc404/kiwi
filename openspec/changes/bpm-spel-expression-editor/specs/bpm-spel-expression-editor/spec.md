## ADDED Requirements

### Requirement: SpEL 表达式编辑器字段类型

系统 SHALL 在流程设计器属性面板中提供 Formly 字段类型 `spel-expression`，用于编辑 `BpmComponentParameter` 对应的字符串值；当参数 `htmlType` 为 `spel-expression` 时 SHALL 使用该编辑器而非普通单行输入。

#### Scenario: 变量与上游输出补全

- **WHEN** 设计者在编辑器中输入 `$` 或 `${` 并开始输入变量名
- **THEN** 补全列表 SHALL 包含图中任意组件输入里已出现过的 `${var}` 变量名，以及沿 `sequenceFlow` 反向可达的、位于当前节点之前的组件任务之 `outputParameters.key`（不含隐藏项）

#### Scenario: 插入变量与 SpEL 片段

- **WHEN** 设计者使用「插入变量」或「插入 SpEL」
- **THEN** 系统 SHALL 在光标处插入对应文本（变量插入为 `${key}` 形式，与流程 IO 分析约定一致）

### Requirement: 与 BPM 编辑器集成

`bpm-editor` 页面 SHALL 通过属性面板加载上述编辑器；`buildSpelVariableSuggestions` SHALL 可从 `bpm-editor` 模块导出供其他代码复用。
