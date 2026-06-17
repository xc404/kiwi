## Why

将外部 REST API（以 OpenAPI / Swagger 描述）手工录入为继承「HTTP 请求」(`httpRequest`) 的 `BpmComponent` 元数据（逐项默认 `url`/`method`/`headers`/`body`）成本高、易不一致。需要在后端提供**可重复的生成规则**，并用规格固化行为，便于评审与回归。

## What Changes

- 新增 HTTP 接口：请求体包含 OpenAPI 3.x 或 Swagger 2.0 文档全文（JSON 或 YAML），服务端解析后为**每个支持的 HTTP 操作**生成**未持久化**的 `BpmComponent` 草稿；执行语义由既有 `HttpRequestActivity`（bean `httpRequest`）提供。
- 子组件 `parentId` 指向 `httpRequest` 父组件在库中的实际 id（通过 `key == "httpRequest"` 解析，通常为 `classpath_httpRequest`）。
- 可选请求字段 `baseUrl`：在文档中 `servers` 为空或为相对路径时用于拼接默认 `url`。
- 在 `BpmComponentService` 中提供 `resolveHttpRequestParentComponentId()` 供生成逻辑使用。

## Capabilities

### New Capabilities

- `bpm-openapi-component-generator`：定义从 OpenAPI/Swagger 生成继承 `httpRequest` 的 BPM 组件元数据及对应 API 行为。

### Modified Capabilities

- （无。）

## Impact

- **后端**：`BpmComponentCtl`、`BpmComponentService`、`OpenApiComponentGenerator`（新建）、`swagger-parser` 依赖。
- **前端**：无必选改动；可选用生成接口批量录入组件。
- **运行时**：生成草稿中的 `url` 可能含路径模板占位符，实际请求行为仍取决于流程中 Camunda 输入映射与 `HttpRequestActivity` 对 `url` 的解析。
