## ADDED Requirements

### Requirement: 从 OpenAPI/Swagger 生成继承 httpRequest 的组件草稿列表

系统 SHALL 提供 `POST /bpm/component/from-openapi` 接口，接受 JSON 请求体，其中 **SHALL** 包含非空的 `spec` 字段，其值为 **OpenAPI 3.x 或 Swagger 2.0** 文档全文（**JSON 或 YAML** 字符串）。

服务端 **SHALL** 使用 `swagger-parser`（或等价实现）解析该文档；Swagger 2.0 **SHALL** 在解析管线中转为 OpenAPI 3 模型后再遍历操作。**SHALL** 对 `spec` 全文长度设置上限，超过上限 **SHALL** 拒绝请求。

请求体 **MAY** 包含 `baseUrl`：非空时 **SHALL** 优先于文档内 `servers` 用于推导默认请求根地址（用于与 path 拼接）。

接口 **SHALL** 返回 `List<BpmComponent>`，其中每一项对应文档中 **一个** HTTP 操作（path + method），且 **SHALL NOT** 在服务端自动持久化该列表（客户端按需调用既有保存接口）。

每个返回项 **SHALL** 将 `type` 设为 `SpringBean`，**SHALL** 将 `parentId` 指向当前环境中「HTTP 请求」父组件在持久化层的 id：实现 **SHALL** 优先在已加载的组件缓存中查找 `key` 为 `httpRequest` 的组件并取其 `id`；若未找到 **SHALL** 回退为 `classpath_httpRequest`（与类路径自动部署的默认 id 规则一致）。

每个返回项 **SHALL** 将 `outputParameters` 置为 `null`，以便与父组件合并时继承 `HttpRequestActivity` 声明的输出定义。

仅 **SHALL** 为下列 HTTP 方法生成组件（与 `HttpRequestActivity` 支持集一致）：`GET`、`HEAD`、`POST`、`PUT`、`PATCH`、`DELETE`。其它方法（如 `OPTIONS`、`TRACE`）**SHALL** 跳过且不视为错误。

当文档无法解析、解析结果不含可遍历的 `paths`、或 `spec` 违反长度/必填约束时，接口 **SHALL** 返回 HTTP 400 及可读错误信息，且 **SHALL NOT** 返回成功生成的列表体。

#### Scenario: 缺少必填字段时拒绝请求

- **WHEN** 请求体缺失或 `spec` 为空或仅空白
- **THEN** 接口 **SHALL** 返回 HTTP 400，且 **SHALL NOT** 返回成功生成的列表体

#### Scenario: 成功生成时包含 httpRequest 父引用

- **WHEN** 请求合法且系统中存在已部署的 `httpRequest` 组件
- **THEN** 每个返回项的 `parentId` **SHALL** 等于该组件的 `id`（通常为 `classpath_httpRequest`）

---

### Requirement: 组件标识、分组与描述

对每个 HTTP 操作，系统 **SHALL** 设置：

- `key`：**SHALL** 以 `openapi_` 为前缀；若存在非空的 `operationId`，**SHALL** 基于其派生 slug；否则 **SHALL** 由 HTTP 方法与 path 派生 slug，以保证在同一文档内可区分。
- `name`：**SHALL** 优先使用操作的 `summary`；否则使用 `operationId`；否则使用「方法 + 空格 + path」形式的字面量。
- `group`：**SHALL** 使用 OpenAPI `tags` 的第一项（若存在且非空）；否则 **SHALL** 使用固定分组名（如 `OpenAPI`）。
- `description`：**SHALL** 包含操作的说明性文本（`description` 或 `summary`）、方法与 path、**MAY** 包含 API 标题与版本；**SHALL** 列出参数概要（名称、`in`、是否 required、描述），便于建模者校对。

#### Scenario: 带 operationId 时 key 可预测

- **WHEN** 某操作具有非空 `operationId`
- **THEN** 对应组件的 `key` **SHALL** 包含基于该 `operationId` 的 slug 段（前缀为 `openapi_`）

---

### Requirement: 覆盖 HttpRequest 输入默认值

对每个生成的组件，系统 **SHALL** 在 `inputParameters` 中声明与父组件同名的参数项以**覆盖默认值**（合并策略下子覆盖父），至少 **SHALL** 包含：

- `method`：默认值为该操作的 HTTP 方法名（大写约定与实现一致）。
- `url`：默认值为「基地址 + path」的拼接结果；基地址 **SHALL** 按 `resolveBaseUrl` 规则从 `baseUrl` 请求字段或文档 `servers[0].url` 得到（去除尾部 `/`）；若二者皆空，**SHALL** 仅使用 path（以 `/` 开头）。path 中的模板段（如 `{id}`）**SHALL** 保留为字面量以便建模者替换。

当操作声明 **JSON** 请求体（内容类型为 `application/json` 或实现认定的 `application/*+json`）且方法不是 `GET` 或 `HEAD` 时，实现 **SHALL** 额外覆盖：

- `headers`：默认值为 JSON 对象字符串，且 **SHALL** 包含 `Content-Type: application/json`；
- `body`：默认值为表示空 JSON 对象的字符串（如 `{}`）。

当不满足上述 JSON 请求体条件，或方法为 `GET`/`HEAD` 时，实现 **SHALL NOT** 为 `headers`/`body` 添加无意义的默认值（可为不覆盖或省略该项，由实现与父级合并行为一致决定）。

#### Scenario: GET 操作无 body 默认值

- **WHEN** 某操作为 GET
- **THEN** 生成项 **SHALL NOT** 依赖非空的 `body` 默认值来完成合法请求（GET 下执行器忽略 body）

#### Scenario: POST JSON 操作带 Content-Type 与空体

- **WHEN** 某操作为 POST 且 requestBody 声明 `application/json`
- **THEN** 生成项 **SHALL** 在覆盖参数中包含 `headers` 与 `body` 的默认值，且 `body` 为空对象形式

---

### Requirement: 与 BpmComponentService 合并行为一致

- **WHEN** 对生成草稿中的任一项调用与列表接口相同的「填充/合并父级属性」逻辑
- **THEN** 合并后的输入参数中 **SHALL** 以子组件提供的同名参数为准（覆盖父级 `HttpRequestActivity` 的 `url`、`method`、`headers`、`body` 等），且 **SHALL** 继承父级未被子项声明的输出参数定义
