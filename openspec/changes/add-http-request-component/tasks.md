## 1. 运行时组件

- [ ] 1.1 在 `kiwi-bpmn-component` 新增 `HttpRequestActivity`：`@Component("httpRequest")`（或最终确定的 bean 名），`@ComponentDescription` 声明输入 `url`、`method`、`headers`、`body`、超时等，以及输出 `statusCode`、`responseBody`、`responseHeaders`
- [ ] 1.2 继承 `AbstractBpmnActivityBehavior`，在 `execute(ActivityExecution)` 中使用 `java.net.http.HttpClient` 发起请求，通过 `ExecutionUtils` 读写变量，成功执行后 `super.leave(execution)`
- [ ] 1.3 为 JSON 头解析、超时、至少一种成功与失败路径添加单元测试（`spring-boot-starter-test`），必要时 Mock HTTP 服务器（如 `com.sun.net.httpserver` 或测试容器视项目惯例）

## 2. 验证

- [ ] 2.1 `kiwi-bpmn-component` 模块测试通过（本地或 CI）
- [ ] 2.2 手动：自动部署开启时，设计器可选「HTTP 请求」；部署流程实例，确认状态码与响应体写入预期变量
