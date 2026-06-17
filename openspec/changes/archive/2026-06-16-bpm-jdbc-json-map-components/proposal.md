## Why

流程中已有 Mongo、HTTP、Slurm 等组件，但缺少与 Mongo 对称的 **JDBC/SQL** 数据访问能力；HTTP/Mongo 返回的 JSON 结果也缺少专用 **字段提取** 组件，设计者只能依赖赋值 + SpEL，成本高且易错。

## What Changes

- 扩展 `@ComponentParameter` 支持 `dictKey`，使 JDBC 组件的 `connection_id` 可在设计器下拉选择已保存连接（`jdbc-connections` 字典）。
- 在 `kiwi-bpmn-component` 新增 **`JdbcActivity`**（`@ConditionalOnBean(JdbcConnectionSupplier)`），通过 SPI 复用 admin 的 `ConnectionService`。
- 在 `kiwi-bpmn-component` 新增 **`JsonMapActivity`**，从流程变量 JSON（字符串或 Map/Document）按 JSON Pointer 映射写入多个流程变量；`mappings` 使用 `assignments-editor`。
- 在 `kiwi-admin/backend` 新增 **`KiwiJdbcConnectionSupplier`** 实现 SPI。

## Capabilities

### New Capabilities

- `bpm-jdbc-activity`：JDBC queryOne / query / update 语义与安全约束。
- `bpm-json-map-activity`：JSON 源解析与 Pointer 映射写入流程变量。

### Modified Capabilities

- （无。）

## Impact

- **运行时**：`kiwi-bpmn-component` 新增若干类；`kiwi-bpmn-core` 注解扩展；backend 新增 SPI 实现。
- **设计器**：自动部署后「数据」组出现 JDBC/SQL，「通用」组出现 JSON 映射。
- **安全**：JDBC 仅允许已保存连接 ID；禁止多语句 SQL。
