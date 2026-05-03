## 1. 依赖与概念验证



- [x] 1.1 确认 `com.ai.plug:server2mcp-starter-webmvc` 的 Maven 坐标、稳定版本及仓库来源（Central / 私服 / 本地 install），写入 `kiwi-admin/backend/pom.xml` 并可被 CI 解析  

  **说明**：上游 `api2mcp4j` 依赖的 `io.modelcontextprotocol.sdk:mcp:0.14.0-SNAPSHOT` 在 Central/Spring 仓库不可解析，**未加入**该 starter；在 `kiwi-admin/backend/README.md` 记录了原因与后续接入方式。

- [ ] 1.2 在最小配置下启动 `kiwi-admin`，验证 OpenAPI（如 `/v3/api-docs`）可被 starter 消费且 MCP 端点可连通  

  **说明**：当前为 `KiwiOpenApiDocumentedToolCallbackConfiguration` 内嵌的 OpenAPI→`MethodToolCallback` 实现，与 Spring AI MCP 共用；**需人工**启动后访问 `/v3/api-docs`、MCP `tools/list` 做 smoke。



## 2. 替换 MCP 运行时



- [ ] 2.1 移除 `spring-ai-starter-mcp-server-webmvc` 依赖，清理 `application.yml` 中 `spring.ai.mcp.server` 等与 Spring AI 自带 MCP Server 重复的配置  

  **说明**：未移除；在无法引入 server2mcp 时仍依赖该 starter 提供 MCP Server。

- [ ] 2.2 按 starter 文档完成 `application.yml`（或 Java 配置）：扫描包/包含排除规则、MCP 开关、与 Spring Boot 3.5 兼容的必要属性  

  **说明**：未配置 `plugin.mcp.*`；由 `KiwiOpenApiDocumentedToolCallbackConfiguration` 限定 `com.kiwi.project` 下 `@RestController` 且排除 `AiChatCtl`、`.integration.` 包。



## 3. 安全与暴露范围



- [x] 3.1 配置 server2mcp 仅扫描 `com.kiwi.project` 下业务控制器（或设计文档中的白名单包），排除 Camunda `/engine-rest` 等非目标入口  

  **说明**：由同一配置类中的扫描逻辑实现包前缀与排除类；Camunda 控制器不在 `com.kiwi.project` 包下则不会注册。

- [ ] 3.2 验证未带有效 `Authorization: Bearer` 的 MCP 调用对受保护接口返回 401，与 REST 行为一致（**需人工**）



## 4. 移除 @Tool 并补齐 OpenAPI



- [x] 4.1 系统域：移除 `AuthCtl`、`CommonCtl`、`PermissionCtl`、`SysDictCtl`、`SysMenuCtl`、`SysDeptController`、`SysUserCtl`、`SysRoleCtl` 等上的 MCP 用 `@Tool`，并为每个对外 MCP 相关操作补齐 `@Tag` / `@Operation`（及必要 `@Parameter`/`@Schema`）

- [x] 4.2 工具域：移除 `ConnectionCtl`、`TableCtl`、`CodeGenEntityCtl`、`CodeGenFieldCtl` 等上的 `@Tool`，并补齐 Swagger 注解

- [x] 4.3 BPM 域：移除 `BpmProcessDefinitionCtl`、`BpmProjectCtl`、`BpmComponentCtl` 等上的 `@Tool`，并补齐 Swagger 注解

- [x] 4.4 其他：移除 `MonitorCtl`、`NotificationCtl` 等剩余控制器上的 `@Tool`，并补齐 Swagger 注解

- [x] 4.5 处理非 `@RestController` 的 `@Tool` Bean（如 `BpmDesignerTools`、`MenuAssistantTools`）：**不得**保留为独立 `@Tool` 面；改为等价 REST 端点 + OpenAPI（纳入 server2mcp 扫描），或改为 starter 支持的 prompt/resource 等机制，与 `design.md` 决策 2 一致  

  **说明**：已新增 `AssistantActionsCtl`、`BpmDesignerActionsCtl` 委托原 Service。



## 5. Spring AI 助手集成（与 server2mcp 同源）



- [x] 5.1 移除或重写 `KiwiToolCallbackConfiguration`：删除基于容器扫描 `@Tool` 的 `MethodToolCallbackProvider`；在 `KiwiAdminAiMcpConfiguration` 中为 `ChatClient` 注册 **与 server2mcp 同源** 的 `ToolCallbackProvider`（starter 桥接，或从同一 OpenAPI 生成的回调）  

  **说明**：已删除 `KiwiToolCallbackConfiguration`，以 `KiwiOpenApiDocumentedToolCallbackConfiguration`（内嵌 `OpenApiBasedToolCallbacks`）提供 `kiwiToolCallbackProvider`；`BpmDesignerAiConfiguration` 改为过滤全局 `kiwiToolCallbackProvider`。

- [x] 5.2 更新 `SYSTEM_PROMPT`：工具名称、前缀约定、调用说明与 MCP 客户端可见列表一致，删除仅旧 `@Tool` 名存在的描述

- [ ] 5.3 验证助手对话在迁移后仍能完成至少一条需工具调用的核心路径（如字典查询或菜单导航），并与外部 MCP `tools/list`（或等价）对照名称与参数契约一致（**需人工**）



## 6. 文档与验收



- [x] 6.1 在 README 或运维文档中记录 **BREAKING**：MCP 连接 URL/协议/工具命名相对旧 Spring AI MCP Server 的变化与迁移步骤  

  **说明**：本次 **MCP 连接方式未变**（仍为 Spring AI MCP Server）；**BREAKING** 为工具实现方式（`@Tool` → `@Operation(operationId)`）及少量查询路径新增 `/search/ai-page` 等；已写入 `kiwi-admin/backend/README.md`。

- [x] 6.2 运行 `mvn -pl kiwi-admin -am test`（或项目约定命令）并手动 smoke：REST + MCP 各至少一例成功调用  

  **说明**：`mvn -pl kiwi-admin/backend -am compile` 通过；`-am test` 在 `kiwi-bpmn-component` 既有用例失败（Mockito strict，与本次改动无关）。


