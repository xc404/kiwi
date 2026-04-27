# Kiwi Admin 后端

**Kiwi** 管理端 **Spring Boot** 主应用：REST API、**Camunda BPM**（引擎 REST 与 Web 控制台）、Sa-Token 鉴权、MyBatis 与 MongoDB 等业务数据访问，以及与前端 `kiwi-admin/frontend` 协作的低代码与流程能力。

更完整的平台说明、仓库结构与 Maven 多模块说明见仓库根 [README.md](../../README.md)。

## 技术栈

| 类别 | 说明 |
|------|------|
| 运行时 | Java 17、Spring Boot 3.4.x |
| Web / 文档 | Spring Web、SpringDoc OpenAPI（Swagger UI） |
| 鉴权 | Sa-Token（Redis） |
| 数据 | MyBatis-Plus、MySQL；Spring Data MongoDB；Redis |
| 流程 | Camunda BPM（Spring Boot Starter、REST、`/engine-rest`、Webapp） |
| AI（可选） | Spring AI Alibaba（DashScope / 通义）、MCP Server（SSE，与 `MenuAssistantTools` 等工具共用） |
| 其他 | Hutool、Velocity（代码生成模板）等 |

模块依赖：`kiwi-common`、`kiwi-bpmn-core`、`kiwi-bpmn-component`、`kiwi-bpmn-external-task`（由父工程 `com.kiwi:kiwi-parent` 聚合版本）。

## 环境要求

- **JDK 17**
- **Maven 3.8+**
- 运行期：**MySQL**、**MongoDB**、**Redis**（连接信息与库名以 `application.yml` 为准）

## 快速开始

在**仓库根目录**（推荐，便于解析父 POM 并构建依赖模块）：

```bash
mvn -pl kiwi-admin/backend -am spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,dev"
```

仅在本目录时（需已 `mvn install` 过父工程或依赖模块）：

```bash
cd kiwi-admin/backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,dev"
```

- 默认 HTTP 端口：**8088**（`server.port`）。
- 若未使用 `application-local.yml`，可去掉上述 profile，并确保环境变量或默认占位符与本地数据库一致；否则启动会因连库失败而报错。

## 配置说明

| 项 | 说明 |
|----|------|
| 主配置 | `src/main/resources/application.yml`（端口、数据源、MongoDB、Redis、Camunda、`app.cors`、AI/MCP 等） |
| MCP 端点 | `spring.ai.mcp.server.sse-endpoint`、`spring.ai.mcp.server.sse-message-endpoint`（及 `SPRING_AI_MCP_SERVER_*` 环境变量）；助手回环基址 `kiwi.ai.mcp.loopback-base-url` |
| 本地覆盖 | 复制 `application-local.example.yml` 为 `application-local.yml` 填写真实连接信息；**勿提交** `application-local.yml`（已在 `.gitignore`） |
| CORS | `app.cors.allowed-origins`，生产环境用环境变量 `APP_CORS_ALLOWED_ORIGINS` 配置实际前端 Origin |
| 敏感项 | 数据库密码、`APP_PASSWORD_SECRET`、`CAMUNDA_ADMIN_PASSWORD`、AI 密钥（如 `KIWI_AI_API_KEY` / `DASHSCOPE_API_KEY`）等建议用环境变量 |

`dev` profile 常用于本地将 MyBatis SQL 输出到控制台（见 `application-dev.yml`），仅调试使用。

### AI 对话与助手

实现类位于 `com.kiwi.project.ai`：普通补全由 `AiChatService` 提供；带工具调用的助手由 `AiAssistantService` 提供（可导航字典页等，工具定义见 `com.kiwi.project.system.ai`）。

| 配置 / 环境变量 | 说明 |
|-----------------|------|
| `kiwi.ai.enabled` | 是否启用 AI 接口，默认 `true`；可用 **`KIWI_AI_ENABLED`** 覆盖。关闭后相关请求会失败并提示。 |
| `spring.ai.dashscope.api-key` | 阿里云 DashScope API Key；通常使用 **`KIWI_AI_API_KEY`** 或 **`DASHSCOPE_API_KEY`**。 |
| `spring.ai.dashscope.chat.options.model` | 模型名，默认 **`qwen-plus`**；可用 **`KIWI_AI_MODEL`** 覆盖。 |
| `spring.ai.mcp.server` | 内置 MCP Server（SSE），与助手共用工具回调；可用 **`SPRING_AI_MCP_SERVER_ENABLED=false`** 关闭。外部 HTTP 调用需携带与业务 API 相同的 **`Authorization: Bearer`** 加登录 Token（见 `application.yml` 中 `instructions` 说明）。 |

| HTTP 接口 | 说明 |
|-----------|------|
| `POST /ai/chat` | 请求体 `{ "messages": [ { "role": "user"\|"assistant"\|"system", "content": "..." } ] }`，返回 `{ "content": "..." }`。需登录（`@SaCheckLogin`）。 |
| `POST /ai/assistant` | 同上消息格式；返回文本 `content`，以及可选的 **`actions`**（如 `{ "type": "navigate", "path": "/default/system/dict", "queryParams": { ... } }`），由前端路由消费。 |

本地示例密钥占位见 **`application-local.example.yml`**。前端使用说明见 **[kiwi-admin/frontend/README.md](../frontend/README.md)** 中「AI 辅助」一节。

## 构建产物

在仓库根：

```bash
mvn -pl kiwi-admin/backend -am clean package
```

可执行包由 `spring-boot-maven-plugin` 重打包生成（具体路径以构建输出为准）。

## 开发与联调

- **OpenAPI / Swagger UI**：服务启动后可通过 SpringDoc 访问，例如 **`/swagger-ui.html`**、**`/swagger-ui/`**；OpenAPI JSON 一般为 **`/v3/api-docs`**（与 `SaTokenConfigure` 中放行路径一致）。
- **Camunda**：与主应用同端口；流程引擎 REST 路径与前端 `camundaEngineRestPath`（默认 `/engine-rest`）保持一致即可。
- **前端**：`kiwi-admin/frontend` 中 `environment.ts` 的 `api.baseUrl` 需指向本服务（默认 `http://localhost:8088`）。

## 源码结构（简要）

| 路径 | 说明 |
|------|------|
| `src/main/java/com/kiwi/framework/` | 框架层（启动类、安全、Web、Swagger 等） |
| `src/main/java/com/kiwi/project/` | 业务：`bpm`、`system`、`tools`（代码生成 / JDBC）、`ai`、`monitor`、`notification` 等 |
| `src/main/resources/` | `application*.yml`、MyBatis XML、Velocity 模板、权限与 BPM 资源等 |

## MCP 与 AI 工具

- **MCP Server**：`spring-ai-starter-mcp-server-webmvc`；工具由 **`KiwiOpenApiSyncMcpToolsConfiguration`** 根据控制器 **`@Operation(operationId, summary)`** 经 `McpToolUtils` 转为 `SyncToolSpecification`。
- **助手**：`ChatClient` 通过本机 `McpSyncClient`（`kiwi.ai.mcp.loopback-base-url` + `spring.ai.mcp.server.sse-endpoint`）与 MCP 对齐工具。

## 相关文档

- 根目录：**[README](../../README.md)**（平台概览、Maven 多模块、配置与运行）
- 前端：**[kiwi-admin/frontend/README.md](../frontend/README.md)**
- 远程部署脚本：**[kiwi-admin/script/README.md](../script/README.md)**
