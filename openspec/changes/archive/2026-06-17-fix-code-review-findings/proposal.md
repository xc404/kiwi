## Why

仓库整体代码审查发现：部分 REST 接口在 Sa-Token「注解鉴权」模式下缺少 `@SaCheckLogin` / `@SaCheckPermission`，与已保护的管理接口不一致，存在未授权访问风险；默认配置中存在明文敏感信息与不适合生产的日志/CORS 设置；且缺少自动化测试与 CI 门禁。需要在不破坏现有业务行为的前提下，系统性补齐安全与工程化基线。

## What Changes

- 为 BPM 与公共 API 等控制器补齐与业务一致的登录/权限注解，或采用全局路由鉴权策略，并保证匿名白名单仅限登录、健康检查等明确路径。
- 将数据库、Redis、Mongo、`app.password.secret`、Camunda 管理员等敏感配置从默认 `application.yml` 中移出，改为占位符 + `application-local.yml` / 环境变量，并更新 `.gitignore` 与 README。
- 默认关闭或降级 MyBatis SQL 打印至 stdout；生产 profile 使用合适日志实现。
- 将 CORS 从全局 `*` 改为按环境配置（开发可宽、生产列明 Origin），并与鉴权策略一并文档化。
- 增加最小可行自动化测试（例如鉴权相关集成测试）与 GitHub Actions（`mvn` + 前端 lint/test 可按阶段启用）。
- 修复审查中提到的次要问题：如 `geNewtProcessId` 命名、代码生成默认包名误导等（不改动业务逻辑）。

## Capabilities

### New Capabilities

- `backend-api-security`: 后端 API 在 Sa-Token 下的一致鉴权行为、匿名白名单、BPM/公共接口的保护策略与验证方式。
- `configuration-secrets`: 默认配置无真实密钥、本地覆盖与文档约定。
- `http-cors-policy`: 跨域策略按环境区分，避免与开放 API 误配。
- `quality-gates-testing-ci`: 最小测试集与 CI 工作流，防止回归。

### Modified Capabilities

（`openspec/specs/` 下当前无既有能力文档；本次均为新增能力规格。）

## Impact

- **代码**：`kiwi-admin/backend` 下多个 `*Ctl.java`、`SaTokenConfigure`、CORS/MyBatis 配置；可选新增 `src/test`。
- **配置**：`application.yml`、`application-local.yml`、`.gitignore`、环境变量约定。
- **仓库根**：可选 `.github/workflows/ci.yml`。
- **依赖**：无强制新版本升级；测试如需 `spring-boot-starter-test` 已通常为传递依赖，按现有 `pom` 核实。
- **运维/开发者**：本地启动需复制示例配置或设置环境变量；生产部署需配置 CORS 与密钥。
