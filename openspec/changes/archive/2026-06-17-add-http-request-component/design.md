## Context

Kiwi BPM 中可复用单元以 `@ComponentDescription` 标注的 **Activity** 形式存在；近期组件（如 `AssignmentActivity`）继承 **`AbstractBpmnActivityBehavior`**，在 `execute(ActivityExecution)` 末尾 **`leave`**。HTTP 组件应与之对齐，便于与 Camunda 活动生命周期一致。

## Goals / Non-goals

**Goals:**

- 支持常见方法：**GET、POST、PUT、PATCH、DELETE**（至少覆盖这些；HEAD/OPTIONS 可作为实现细节或同表扩展）。
- **请求头**、**请求体**可选；请求体对 JSON/纯文本以字符串形式传递即可（与属性面板 `#text` 一致）。
- **连接超时**与**响应超时**可配置（秒或毫秒，与实现统一文档化），有合理默认值。
- **响应**：将 **状态码**（整数或字符串）、**响应体**（字符串）、**响应头**（序列化为 JSON 对象字符串，便于后续赋值或 Mongo 等组件消费）写入由输出参数映射的流程变量。

**Non-goals:**

- 不在此变更中实现 **OAuth2 签名、mTLS、双向证书** 等高级 TLS 客户端能力。
- 不实现 **异步 / 非阻塞** 调用模型（保持与服务任务同步执行一致）。
- **SSRF 防护**（URL 白名单、内网禁止访问）不作为本变更必交付项；在 proposal 中已列为环境与流程设计责任，可在后续独立 change 增强。

## Decisions

1. **类与 Bean 名**：例如 `com.kiwi.bpmn.component.activity.HttpRequestActivity`，`@Component("httpRequest")`（或 `httpRequestActivity`，与团队命名一致即可），`@ComponentDescription` 的 `name` 建议「HTTP 请求」，`group` 建议 `集成` 或 `通用`。
2. **HTTP 客户端**：使用 **`java.net.http.HttpClient`**，单例或按类静态复用（注意线程安全：`HttpClient` 可安全复用）。
3. **输入契约**（建议，实现时可微调键名但需在 spec 与元数据中一致）：
   - `url`（必填，`#text`）：绝对 URL，`http` 或 `https`。
   - `method`（可选，默认 `GET`）：枚举字符串，大小写不敏感。
   - `headers`（可选，`#text`）：**JSON 对象**字符串，如 `{"Content-Type":"application/json"}`；缺失或空表示无额外头（实现可合并默认 `User-Agent` 等，若加则文档说明）。
   - `body`（可选，`#text`）：请求体；GET 等无体方法应忽略或允许为空。
   - `connectTimeoutSeconds`、`readTimeoutSeconds`（可选）：正数，默认例如 10 / 30（具体数值在实现与 spec 中写死为一致）。
4. **输出契约**：与 `ShellActivityBehavior` 类似，使用 **`ExecutionUtils.getOutputVariableName`** 解析「逻辑输出名 → 实际流程变量名」：
   - `statusCode` → HTTP 状态码；
   - `responseBody` → 响应体字符串（大响应注意内存；非目标优化可注明）；
   - `responseHeaders` → 响应头 Map 的 JSON 字符串（多值头可序列化为数组或取首值，选一种并在 spec 中固定）。
5. **错误语义**：URL 非法、超时、无法连接、`headers` 非 JSON 对象时，抛出明确运行时异常，使流程实例失败可追踪（与 `AssignmentActivity` 一类组件一致）。
6. **字符编码**：响应体使用 **UTF-8** 解码为字符串；若需二进制，非本变更范围。

## Risks / Trade-offs

- **[Risk] SSRF**：任意 URL 可能被滥用于访问内网 → 文档与非目标项中说明；生产建议网络策略或后续白名单 change。
- **[Trade-off] 大响应体**：全部读入内存 → 与脚本类组件类似，接受；超大场景用专用集成或流式扩展。

## Migration Plan

- 无数据迁移；新组件随应用启动与自动部署进入库。
- 回滚：移除类或取消部署即可；已部署流程若引用该 `componentId` 需人工改图。

## Open Questions

- 无（若产品要求默认禁止重定向，可在实现中将 `HttpClient` 的 `Redirect` 策略设为 `NEVER` 并写入 spec）。
