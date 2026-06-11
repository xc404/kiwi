## Context

- 组件扫描：`ClasspathBpmComponentProvider` 收集带 `@ComponentDescription` 的 Spring Bean。
- JDBC 连接配置在 Mongo `toolConnectionSettings`，运行时经 `ConnectionService.getConnection(id)` 打开。
- `kiwi-bpmn-component` 不可依赖 backend，故 JDBC 连接通过 `JdbcConnectionSupplier` SPI 注入。

## Decisions

1. **JdbcConnectionSupplier SPI** 放在 `kiwi-bpmn-component`；实现放在 backend。
2. **JdbcActivity** 操作：`queryOne` | `query` | `update`；参数 `params` 为 JSON 数组绑定 PreparedStatement。
3. **JsonMapActivity** 使用 Jackson JSON Pointer（`JsonNode.at`），不引入 Jayway JsonPath。
4. **dictKey** 加到 `@ComponentParameter`，由 `ComponentUtils` 映射到 `BpmComponentParameter`。

## Non-Goals

- 不修改 `AssignmentActivity` 空壳实现。
- 不做跨节点 JDBC 事务、存储过程、JsonPath 语法。
