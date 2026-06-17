## Why

路线图阶段二「BPM 组件测试矩阵」要求为 Shell、File、Mongo 等高频组件补齐单测；当前 `kiwi-bpmn-component` 已有 HTTP/JDBC/JsonMap 测试，但上述核心 I/O 组件仍无覆盖，回归风险高且与 CI `bpmn-test` job 目标不一致。

## What Changes

- 为 `ShellActivityBehavior`、`FileReadActivity`、`FileWriteActivity`、`MongoActivity` 新增 JUnit 5 + Mockito 单测。
- 测试风格对齐现有 `HttpRequestActivityTest`、`JdbcActivityTest`：mock `ActivityExecution` / `DelegateExecution`，必要时使用临时文件或嵌入式资源，不启动 Spring 容器。
- 覆盖 happy path、必填校验、路径/JSON 非法输入等关键分支；Shell 使用平台无关 echo 命令验证 stdout 与 exit code。

## Capabilities

### New Capabilities

- `bpm-component-unit-tests`：高频 BPM 组件（Shell、File 读/写、Mongo）的单元测试覆盖要求与 CI 可执行性。

### Modified Capabilities

- （无。仅增加测试，不改变组件运行时行为。）

## Impact

- **代码**：`kiwi-bpmn-component/src/test/java` 新增 4 个测试类。
- **CI**：现有 `bpmn-test` job 自动执行新增测试，无 workflow 变更。
- **依赖**：复用 `spring-boot-starter-test`、H2（已有）；Mongo 测试 mock `MongoTemplate`，无需 Testcontainers。
