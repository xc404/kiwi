## 1. 配置与密钥卫生

- [x] 1.1 新增 `application-local.example.yml`（或等价模板），列出数据源、Mongo、Redis、Camunda、`app.password.secret` 等占位项；在 README 说明复制为 `application-local.yml` 并激活。
- [x] 1.2 将 `application.yml` 中真实样式口令替换为占位符或 `${ENV_VAR}`，并更新 `.gitignore` 忽略 `**/application-local.yml`（若与现有规则冲突则合并）。
- [x] 1.3 为 `local`/`dev` profile 保留可选 MyBatis 调试；默认或 `prod` profile 使用 `Slf4jImpl` 或关闭映射 SQL 日志，移除 `StdOutImpl` 作为默认。

## 2. API 鉴权统一

- [x] 2.1 在 `SaTokenConfigure`（或集中配置类）中显式登记匿名白名单：`/auth/signin`、`/auth/signout`、Swagger/OpenAPI 路径（若启用）等；文档化列表。
- [x] 2.2 为 `BpmProcessDefinitionCtl` 全部对外方法添加 `@SaCheckLogin`（及与 `permission.json` 对齐的 `@SaCheckPermission`，若已有 BPM 相关 permission 字符串）。
- [x] 2.3 为 `BpmComponentCtl` 全部变更与查询方法添加 `@SaCheckLogin`（及权限注解，若适用）。
- [x] 2.4 为 `BpmProjectCtl` 中缺失的 `get`/`update`/`delete` 补充 `@SaCheckLogin`，与列表/新增一致。
- [x] 2.5 审查 `CommonCtl`：`/common/dict/**`、`/common/tree/**` 按 design 决策添加 `@SaCheckLogin`；若产品要求匿名只读，则单独开 issue 并改 spec。
- [x] 2.6 全局搜索 `RestController`/`@Controller` 下无 Sa 注解的 `@*Mapping`，补全或列入白名单并注释原因。

## 3. CORS 与跨环境行为

- [x] 3.1 用可配置属性（如 `app.cors.allowed-origins`）驱动 `CorsConfig`，本地 profile 包含 `http://localhost:4201`；生产禁止仅依赖 `*`（除非明确无 cookie 且文档说明）。
- [x] 3.2 在 README「配置说明」中增加 CORS 与生产 Origin 列表的说明。

## 4. 测试与 CI

- [x] 4.1 在 `kiwi-admin/backend` 增加至少一个鉴权相关测试（MockMvc 或 `@SpringBootTest`）：未带 token 访问受保护 BPM 路径返回非 2xx 或与项目统一的未登录码。
- [x] 4.2 添加 `.github/workflows/ci.yml`：`mvn -pl kiwi-admin/backend -am test`；Node 下 `cd kiwi-admin/frontend && npm ci && npm run lint`（首次可对 lint 失败采用 `continue-on-error: true` 并在注释中写明后续收紧）。
- [x] 4.3 若集成测试依赖外部 DB，对重依赖用例使用 Testcontainers、`@MockBean` 或 profile 隔离，保证默认 `mvn test` 在 CI 可跑通。

## 5. 小修复与收尾

- [x] 5.1 将 `geNewtProcessId` 重命名为正确拼写（如 `getNewProcessId`），全局替换并编译通过。
- [x] 5.2 将 `application.yml` 中 `app.gen.packageName` 的默认包改为 `com.kiwi` 域下合理占位（如 `com.kiwi.project`），避免误导性 `treehole`。
- [x] 5.3 本地全量验证：登录、BPM 设计/部署、字典与菜单加载；更新变更说明供合并用。
