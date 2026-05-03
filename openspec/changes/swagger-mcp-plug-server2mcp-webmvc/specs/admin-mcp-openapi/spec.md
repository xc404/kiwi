## ADDED Requirements

### Requirement: MCP 由 OpenAPI 驱动暴露

Kiwi 管理后台 SHALL 通过 `com.ai.plug:server2mcp-starter-webmvc`（或经项目批准的同族构件）对外提供 MCP 能力，工具（及 starter 支持的其他 MCP 构造）的主要元数据 SHALL 来源于 Spring Web 控制器与 Springdoc 生成的 OpenAPI 3 描述，而非分散的 `org.springframework.ai.tool.annotation.Tool` 方法注册。

#### Scenario: 新 REST 端点默认可被 MCP 消费

- **WHEN** 开发者在已纳入扫描范围的 `@RestController` 上新增符合 OpenAPI 描述的 HTTP 端点，并完成必需的 Swagger 3 注解（见「OpenAPI 注解完整性」）
- **THEN** 该端点所对应的操作 SHALL 出现在 MCP 工具（或 starter 映射的等价能力）列表中，且摘要/参数说明与 OpenAPI 文档一致，无需新增 `@Tool`

### Requirement: 弃用基于 @Tool 的 MCP 与助手工具注册

为 MCP 或助手 tool-calling 而存在于控制器、服务或工具类上的 `@Tool` 标注 SHALL 被移除。`KiwiToolCallbackConfiguration` 中基于「扫描 `@Tool` 方法」的 `MethodToolCallbackProvider` SHALL 被移除或替换，**不得**再作为 MCP 或 `ChatClient` 的工具来源。

#### Scenario: 控制器无 MCP 专用注解

- **WHEN** 审计原先带 `@Tool` 的管理后台控制器
- **THEN** 这些类 SHALL 不再包含为 MCP 而写的 `@Tool`，MCP 客户端 SHALL 仍能通过对应用途的 MCP 调用完成等价的业务能力（由 OpenAPI 映射实现）

### Requirement: ChatClient 与 server2mcp 工具面一致

Spring AI `ChatClient` 所注册的 `ToolCallbackProvider`（或等价机制）SHALL 与 server2mcp 从 OpenAPI 暴露的业务工具在**名称与参数契约**上一致；实现 SHALL 使用 starter 提供的 Spring AI 集成，或从**与 server2mcp 相同的 OpenAPI 文档**生成/包装为 `ToolCallback`。SHALL NOT 为助手单独维护一套与 MCP 列表不同的 `@Tool` 扫描结果。

#### Scenario: 助手与外部 MCP 工具名一致

- **WHEN** 列出外部 MCP 客户端可见的工具名与助手对话中模型可见的工具名（或等价元数据）
- **THEN** 二者在业务工具集合上 SHALL 一致；允许仅传输/封装层差异，不得出现仅助手可见或仅 MCP 可见的重复目的工具

#### Scenario: 系统提示与真实工具对齐

- **WHEN** 用户通过助手发起需调用工具的对话
- **THEN** `SYSTEM_PROMPT` 中描述的工具名与调用方式 SHALL 与已注册且与 OpenAPI/server2mcp 一致的工具有效集合匹配，不得引用已删除的 `@Tool` 名称

### Requirement: OpenAPI 注解完整性

所有通过 MCP 暴露的 HTTP API 分组 SHALL 具备类级 `@Tag`（或 Springdoc 认可的等价分组元数据）。每个暴露为 MCP 工具的操作 SHALL 具备可读的方法级描述：至少 `@Operation(summary = ...)`，若行为非显而易见则 SHALL 包含 `description`。路径变量、查询参数或请求体字段若不能从类型名推断语义，则 SHALL 通过 `@Parameter`、`@Schema` 或 DTO 字段注解补充说明。

#### Scenario: 缺省文档不足时不予合并

- **WHEN** 代码评审或 CI 中的 OpenAPI/MCP 契约检查（若已配置）发现某暴露端点缺少 `@Tag` 或 `@Operation(summary)`
- **THEN** 该变更 SHALL 在补齐注解前不视为完成

### Requirement: 认证与暴露范围

MCP 调用 SHALL 使用与现有 REST API 相同的安全机制（例如 `Authorization: Bearer` 与 Sa-Token 会话校验）。starter SHALL 配置为仅扫描 Kiwi 管理业务 API 包（或显式白名单），SHALL 不默认将 Camunda `/engine-rest`、第三方引擎 UI 等非本应用 OpenAPI 管理的端点暴露为 MCP，除非产品规格明确要求。

#### Scenario: 未认证调用被拒绝

- **WHEN** MCP 客户端在未携带有效 Bearer Token 的情况下调用需登录的管理接口
- **THEN** 服务器 SHALL 返回与 REST 一致的错误语义（如 401），且 SHALL 不泄露敏感实现细节

### Requirement: 可运维配置与破坏性变更说明

项目 SHALL 在配置文件中提供启用/禁用或调参 server2mcp 的开关（具体键名以 starter 为准）。README 或运维文档 SHALL 说明相对原 `spring-ai-starter-mcp-server-webmvc` 的 **BREAKING** 项：连接 URL、传输方式、工具命名规则的变化及迁移建议。

#### Scenario: 运维可关闭 MCP

- **WHEN** 运维将 MCP 相关功能设为关闭（环境变量或配置属性）
- **THEN** 应用 SHALL 正常启动，且 SHALL 不对外提供 MCP 监听（或等价「不可用」行为），管理后台 HTTP API SHALL 不受影响
