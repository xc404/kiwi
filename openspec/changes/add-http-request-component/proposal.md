## Why

流程中经常需要调用 **HTTP/HTTPS** 接口（Webhook、内部 REST、健康检查等）。当前组件库已有 Shell、Mongo、赋值等，但缺少一种不依赖外部 Worker、由引擎内同步执行的「HTTP 请求」类服务任务组件；设计者目前只能写脚本或另起集成方式，成本高且难统一观测（状态码、响应体）。

## What Changes

- 在 `kiwi-bpmn-component` 中新增 **`HttpRequestActivity`**（Spring Bean，继承 `AbstractBpmnActivityBehavior`），通过 `@ComponentDescription` 注册为 BPM **组件**，由现有 `ClasspathBpmComponentProvider` 扫描并在自动部署场景下进入设计器可选项。
- 使用 JDK 自带的 **`java.net.http.HttpClient`**（模块已使用 Java 17），避免引入 Spring Web / OkHttp 等新依赖。
- 通过组件参数声明：**请求 URL、HTTP 方法、可选请求头/请求体、超时**；输出 **HTTP 状态码、响应体、响应头（JSON 字符串）** 到可配置的流程变量名（与现有 `ExecutionUtils.getOutputVariableName` 约定一致）。
- 不修改 BPMN 图元类型：仍使用 **`bpmn:ServiceTask` + `componentId`**（与其它 SpringBean 组件一致）。

## Capabilities

### New Capabilities

- `add-http-request-component`：定义 HTTP 请求组件的请求构建、超时、响应写入流程变量的语义。

### Modified Capabilities

- （无。）

## Impact

- **运行时**：`kiwi-bpmn-component` 新增一个类及建议配套的单元测试；无新增外部依赖。
- **设计器**：组件列表来自后端元数据，部署/刷新后调色板可出现新组件。
- **安全**：调用任意 URL 存在 **SSRF** 风险；本变更在设计与规格中明确为「由流程设计者与部署环境负责」，可选后续变更增加主机白名单等非目标项。
