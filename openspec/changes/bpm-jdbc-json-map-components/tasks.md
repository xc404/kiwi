## 1. 元数据

- [x] 1.1 `@ComponentParameter` 增加 `dictKey`；`ComponentUtils.toComponentProperty` 映射

## 2. JDBC 组件

- [x] 2.1 新增 `JdbcConnectionSupplier` SPI（kiwi-bpmn-component）
- [x] 2.2 新增 `KiwiJdbcConnectionSupplier`（backend）
- [x] 2.3 新增 `JdbcActivity` + `JdbcActivityTest`

## 3. JSON 映射组件

- [x] 3.1 新增 `JsonMapExecutor` + `JsonMapActivity`
- [x] 3.2 新增 `JsonMapExecutorTest` / `JsonMapActivityTest`

## 4. 验证

- [x] 4.1 `mvn -pl kiwi-bpmn/kiwi-bpmn-component -am test` 通过（新增测试类）
- [x] 4.2 `mvn -pl kiwi-admin/backend -am compile` 通过
