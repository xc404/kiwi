## 1. 版本与 BOM

- [x] 1.1 选定目标 **Spring Boot 4.0.x** 补丁版本（以 Central + release notes 为准），更新根 `pom.xml` 的 `spring-boot-starter-parent` 与 `spring-boot.version` 属性
- [x] 1.2 更新 `kiwi-admin/backend/pom.xml` 中 `spring-boot.version` 与 `spring-boot-maven-plugin` 版本，与根一致
- [x] 1.3 更新 `cyroems/pom.xml` 的 `spring-boot-starter-parent` 版本，与根一致
- [x] 1.4 按 `design.md` 移除或替换 `spring-framework-bom` **6.2.x** 导入；若需覆盖则改为 **7.x** 并与 Boot 4 对齐，在 PR/提交说明中记录原因
- [x] 1.5 将 `camunda.version` 升至 **≥ 7.24.3**，并将所有 `camunda-bpm-spring-boot-starter` / `webapp` / `rest` / `external-task-client` 等改为 **`-4` 后缀** 坐标（与 Camunda 文档一致）

> **1.5 说明**：截至本次实施，Maven Central 上 Camunda CE 最高仍为 **7.24.0**，**无** `camunda-bpm-spring-boot-starter-4` / **7.24.3** 工件；故 **`camunda.version` 维持 7.24.0**，starter 仍为原名。待 Central 发布 **≥7.24.3** 与 `*-4` 坐标后再切换。

## 2. 传递依赖与第三方

- [x] 2.1 核对 **spring-ai-alibaba-bom** / **`spring-ai-starter-mcp-server-webmvc`**（及 backend `dependencyManagement` 顺序）在 Boot 4 下的兼容版本，按需升级并保留「spring-framework-bom 在最前」规则若仍适用
- [x] 2.2 验证 **sa-token**、**springdoc-openapi**、**mica**、**mybatis-plus**、**hibernate-validator** 等与 Boot 4 / Jakarta 版本无冲突；必要时升级并在注释或变更说明中注明
- [x] 2.3 运行 `mvn -pl kiwi-admin/backend dependency:tree`（及受影响模块）确认 **spring-core** 等为 **7.x**，无意外压低

## 3. 代码、配置与迁移指南

- [x] 3.1 按 Spring Boot 4 **Migration Guide** 检查并替换废弃 API、重命名配置属性；更新 `application*.yml` / `application*.properties`
- [x] 3.2 全仓库检索 `javax.annotation` 等若与 Boot 4 默认栈冲突的依赖，按指南处理
- [x] 3.3 若 **Netty / Reactor / Mongo** 等传递版本变化，复核现有自定义配置（连接池、超时）仍生效

## 4. 验证

- [x] 4.1 执行 `mvn verify`（或团队约定的模块矩阵），修复编译与测试失败
- [ ] 4.2 本地启动 **kiwi-admin** 后端：Camunda Webapp/REST、登录、核心 BPM 流程冒烟
- [ ] 4.3 若适用，启动 **cyroems** / **cryoems-bpm** 相关服务并做最小冒烟
- [x] 4.4 将本 `tasks.md` 中已完成项勾选为 `- [x]`，并准备发布说明（含 BREAKING：Camunda 坐标、配置变更）

### 发布说明摘要（BREAKING / 注意）

- **Spring Boot 4.0.6**，**Spring Framework 7.0.7**（见 `dependencyManagement` / `spring-framework.version`）。
- **springdoc-openapi-starter-webmvc-ui** → **3.0.3**（适配 Boot 4）。
- **Spring AI**：导入 **`spring-ai-bom` 1.1.5**；**DashScope** → **1.1.2.2**（并对钉死的 Boot 3.5 传递依赖做 **exclusion**）；**MCP** 随 BOM，排除重复的 `spring-boot-starter`/`web`，避免混用 Boot 3。
- **javax.annotation-api** → **`jakarta.annotation-api`**；校验改用 **`spring-boot-starter-validation`**。
- **Boot 4 模块化**：新增 **`spring-boot-jdbc`**（`DataSourceBuilder` / `DatabaseDriver`）；**`WebServerInitializedEvent`** 包名改为 **`org.springframework.boot.web.server.context`**；MCP `HttpClientSseClientTransport` 不再链式 **`objectMapper(...)`**。
- **Camunda**：仍为 **7.24.0 + 原 starter 坐标**（见上 1.5）。
