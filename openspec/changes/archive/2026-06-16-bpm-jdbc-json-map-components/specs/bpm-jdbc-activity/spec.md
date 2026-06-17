## ADDED Requirements

### Requirement: JDBC 组件注册

系统 SHALL 提供 Spring Bean `jdbcActivity`，继承 `AbstractBpmnActivityBehavior`，使用 `@ComponentDescription`（group=`数据`），且仅在存在 `JdbcConnectionSupplier` Bean 时注册。

#### Scenario: 元数据含 connection_id 字典

- **WHEN** 读取组件元数据
- **THEN** 输入参数 SHALL 包含 `connection_id`（required，`dictKey=jdbc-connections`）、`operation`、`sql`、`params`、`max_rows`、`query_timeout_seconds`；输出 SHALL 包含 `result`

### Requirement: JDBC 执行语义

执行时系统 SHALL 通过 `connection_id` 打开连接，按 `operation` 执行 SQL：`queryOne` 返回首行 Map 或 null；`query` 返回 List<Map> 且受 `max_rows` 限制；`update` 返回影响行数 int。

#### Scenario: queryOne 成功

- **WHEN** `operation=queryOne` 且 SQL 返回一行
- **THEN** 结果变量 SHALL 为列名到值的 Map

#### Scenario: 禁止多语句

- **WHEN** SQL 含多个语句（分号后仍有非空内容）
- **THEN** 执行 SHALL 失败
