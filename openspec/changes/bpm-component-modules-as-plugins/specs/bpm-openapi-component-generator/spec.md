## MODIFIED Requirements

### Requirement: 从 OpenAPI/Swagger 生成继承 httpRequest 的组件草稿列表

系统 SHALL 提供 `POST /bpm/component/from-openapi` 接口，接受 JSON 请求体，其中 **SHALL** 包含非空的 `spec` 字段，其值为 **OpenAPI 3.x 或 Swagger 2.0** 文档全文（**JSON 或 YAML** 字符串）。

服务端 **SHALL** 使用 `swagger-parser`（或等价实现）解析该文档；Swagger 2.0 **SHALL** 在解析管线中转为 OpenAPI 3 模型后再遍历操作。**SHALL** 对 `spec` 全文长度设置上限，超过上限 **SHALL** 拒绝请求。

请求体 **MAY** 包含 `baseUrl`：非空时 **SHALL** 优先于文档内 `servers` 用于推导默认请求根地址（用于与 path 拼接）。

接口 **SHALL** 返回 `List<BpmComponent>`，其中每一项对应文档中 **一个** HTTP 操作（path + method），且 **SHALL NOT** 在服务端自动持久化该列表（客户端按需调用既有保存接口）。

每个返回项 **SHALL** 将 `type` 设为 `SpringBean`，**SHALL** 将 `parentId` 指向当前环境中「HTTP 请求」父组件在持久化层的 id：实现 **SHALL** 优先在已加载的组件缓存中查找 `key` 为 `httpRequest` 的组件并取其 `id`；若未找到 **SHALL** 回退为 `plugin_httpRequest`（与插件自动部署的默认 id 规则一致）。

每个返回项 **SHALL** 将 `outputParameters` 置为 `null`，以便与父组件合并时继承 `HttpRequestActivity` 声明的输出定义。

仅 **SHALL** 为下列 HTTP 方法生成组件（与 `HttpRequestActivity` 支持集一致）：`GET`、`HEAD`、`POST`、`PUT`、`PATCH`、`DELETE`。其它方法（如 `OPTIONS`、`TRACE`）**SHALL** 跳过且不视为错误。

当文档无法解析、解析结果不含可遍历的 `paths`、或 `spec` 违反长度/必填约束时，接口 **SHALL** 返回 HTTP 400 及可读错误信息，且 **SHALL NOT** 返回成功生成的列表体。

#### Scenario: 缺少必填字段时拒绝请求

- **WHEN** 请求体缺失或 `spec` 为空或仅空白
- **THEN** 接口 **SHALL** 返回 HTTP 400，且 **SHALL NOT** 返回成功生成的列表体

#### Scenario: 成功生成时包含 httpRequest 父引用

- **WHEN** 请求合法且系统中存在已部署的 `httpRequest` 组件
- **THEN** 每个返回项的 `parentId` **SHALL** 等于该组件的 `id`（通常为 `plugin_httpRequest`）
