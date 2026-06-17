## Context

`BpmComponent` 支持 `parentId` 与 `BpmComponentService#fillComponentProperties` 的**按 key 合并**：子组件声明的输入参数与同名父参数合并时，**子覆盖父**。内置「HTTP 请求」组件由 `HttpRequestActivity`（Spring bean `httpRequest`）经 `ClasspathBpmComponentProvider` 部署，数据库 id 一般为 `source_key` 即 `classpath_httpRequest`。

OpenAPI 文档由 `io.swagger.parser.v3:swagger-parser` 解析；Swagger 2.0 在解析管线中转为 OpenAPI 3 模型。

## Goals / Non-goals

**Goals:**

- 对每个 **path + HTTP 方法** 生成一条组件草稿，默认覆盖父级 `HttpRequestActivity` 的 `url`、`method`，并在适用时预置 `headers`（如 `Content-Type`）与 `body`（如空 JSON 对象）。
- 仅处理与 `HttpRequestActivity` 一致的 HTTP 方法：`GET`、`HEAD`、`POST`、`PUT`、`PATCH`、`DELETE`。
- `key` 稳定可辨：优先使用 `operationId`（经 slug）；否则由方法与 path 派生。

**Non-goals:**

- 不保证覆盖 OpenAPI 中所有扩展或非标结构；解析失败时返回可诊断错误。
- 不自动为路径参数生成独立流程变量绑定；路径模板可保留在默认 `url` 字符串中，由建模者在流程中替换或使用表达式。
- 不在此变更中修改 `HttpRequestActivity` 的执行语义或增加 OpenAPI 专属执行路径。

## Decisions

1. **接口**：`POST /bpm/component/from-openapi`，请求体包含必填 `spec`（全文），可选 `baseUrl`；返回 `List<BpmComponent>`，**不自动写库**。
2. **父组件 id**：通过缓存中 `key == "httpRequest"` 解析；若不存在则回退字符串 `classpath_httpRequest`。
3. **默认 `url`**：`resolveBaseUrl`（`baseUrl` 优先，否则 `servers[0].url`）与 path 拼接；无 server 且未传 `baseUrl` 时仅为 path（以 `/` 开头）。
4. **JSON 请求体**：当存在 `application/json`（或 `application/*+json`）的 requestBody 时，对非 GET/HEAD 预置 `headers` 为 `{"Content-Type":"application/json"}`，`body` 默认为 `{}`。
5. **校验**：`spec` 为空或过长时返回 HTTP 400；解析失败或文档无 `paths` 时返回 HTTP 400 及可读原因。
6. **文档大小**：对 `spec` 全文长度设上限，防止滥用。

## Risks / Trade-offs

- **[Trade-off]** 规范中 `servers` 与部署环境不一致时，默认 `url` 可能需人工调整。
- **[Trade-off]** `operationId` 冲突或缺失时的 key 唯一性依赖 slug 规则；极端情况下需人工改 `key` 后再保存。

## Migration Plan

- 无数据迁移；新接口与生成逻辑为增量能力。

## Open Questions

- 是否在后续迭代支持「从 URL 拉取规范」或批量持久化；本规格不包含。
