## ADDED Requirements

### Requirement: HTTP 请求组件注册为 Spring Bean Activity

系统 SHALL 提供名为 `httpRequest`（若实现采用其它 bean 名，规格与元数据 MUST 一致）的 Spring 组件类，该类 MUST 继承 Camunda `AbstractBpmnActivityBehavior`，MUST 使用 `@ComponentDescription` 暴露为 BPM 组件（类型为 Spring Bean，与其它 Activity 一致），且 MUST 通过现有组件扫描与部署机制出现在可配置的服务任务组件列表中。

#### Scenario: 组件元数据包含 URL 与输出映射

- **WHEN** 读取该组件的 `ComponentDescription` 元数据
- **THEN** 其输入参数 SHALL 包含键为 `url` 的参数（用于请求地址），且 SHALL 包含用于映射 HTTP 状态码、响应体、响应头写入目标的输出参数定义（键名与 `Shell`/`Assignment` 等组件的输出约定一致，通过流程变量配置目标变量名）

### Requirement: 发起 HTTP 请求并写入流程变量

当服务任务执行该组件时，系统 SHALL 从流程变量中读取至少 `url` 及实现所声明的其它输入（方法、头、体、超时等），并使用 JDK `java.net.http.HttpClient` 发起同步 HTTP 请求。成功收到响应后，系统 SHALL：

- 将 **HTTP 状态码** 写入由输出参数 `statusCode` 所映射的流程变量（若用户未映射该输出，行为以实现与文档为准，但 MUST NOT 因未映射而抛无意义异常）；
- 将 **响应体** 以字符串形式（UTF-8）写入由 `responseBody` 所映射的流程变量；
- 将 **响应头** 以 JSON 字符串形式写入由 `responseHeaders` 所映射的流程变量（多值头的表示方式在实现中固定并文档化）。

连接失败、超时、非法 URL、或请求头 JSON 无法解析为对象时，系统 SHALL 使活动失败并产生可诊断错误。

#### Scenario: GET 请求成功

- **WHEN** `url` 指向可访问的 HTTP 资源且方法为 GET（或默认 GET）
- **THEN** 映射后的流程变量 SHALL 包含非空状态码，且 `responseBody` SHALL 包含服务端返回的主体（可为空字符串）

#### Scenario: 带 JSON 头的 POST

- **WHEN** `method` 为 POST，`headers` 为合法 JSON 对象且包含 `Content-Type: application/json`，`body` 为合法 JSON 字符串
- **THEN** 系统 SHALL 发送该体与头，且 SHALL 按上一 requirement 写入响应相关变量

#### Scenario: 超时失败

- **WHEN** 服务端在配置的超时时间内无响应或连接无法在连接超时内建立
- **THEN** 执行 SHALL 失败并报告与超时相关的错误

### Requirement: 执行后正常离开

成功完成 HTTP 调用与变量写入后，该 Activity SHALL 调用 `leave`，按 Camunda 活动语义继续流出，不得在无错误情况下滞留。

#### Scenario: 继续流转

- **WHEN** HTTP 请求成功且变量已写入
- **THEN** 流程 SHALL 进入该服务任务之后的顺序流
