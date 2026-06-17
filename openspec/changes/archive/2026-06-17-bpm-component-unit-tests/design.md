## Context

- 组件位于 `kiwi-bpmn-component`；已有测试模式见 `HttpRequestActivityTest`（mock execution + spy leave）、`JdbcActivityTest`（H2 内存库 + supplier 注入）。
- `ShellActivityBehavior` 实现 `JavaDelegate`（`DelegateExecution`）；File/Mongo 继承 `AbstractBpmnActivityBehavior`（`ActivityExecution`）。
- `MongoActivity` 构造注入 `MongoTemplate`；单测直接 mock，不依赖 `@ConditionalOnBean` 条件。

## Decisions

1. **不引入 Testcontainers / SpringBootTest**：与现有 bpmn 模块测试一致，保持 `mvn test` 轻量、无外部服务。
2. **File 测试使用 `@TempDir`**：读写真实临时文件，验证 encoding、append、maxBytes、路径安全。
3. **Shell 测试使用 echo**：Windows `cmd /c echo hello`、Unix `echo hello` 由组件内部 `ProcessBuilder` 处理；断言 `result` 含预期文本、`errorCode` 为 `0`。
4. **Mongo 测试 mock MongoTemplate**：覆盖 findOne / insert / count、非法 JSON、空 collection；updateOne/deleteOne 通过 mock `MongoCollection` 验证调用链。
5. **静态辅助方法**：`FileReadActivity.rejectUnsafePath`、`ShellActivityBehavior.convertStreamToStr` 通过 public/package 可见 API 或 execute 间接覆盖，不新增生产代码仅为测试。

## Non-Goals

- 不测试 SFTP、Email、Sleep 等低频组件（后续迭代）。
- 不修改组件实现或 `@ComponentParameter` 契约。
- 不建立 backend `@SpringBootTest` 集成基座（路线图独立项）。
