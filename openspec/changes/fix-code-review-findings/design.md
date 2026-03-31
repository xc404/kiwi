## Context

Kiwi 后端使用 Sa-Token，通过 `SaInterceptor` 启用注解鉴权。审查发现 `BpmProcessDefinitionCtl`、`BpmComponentCtl` 等控制器方法普遍缺少 `@SaCheckLogin` 或细粒度 `@SaCheckPermission`，而系统管理类接口已有权限注解；在 Sa-Token 默认「仅校验带注解的接口」模式下，未标注方法可能对匿名用户开放。同时默认 `application.yml` 含本地口令与 `app.password.secret`，MyBatis 使用 `StdOutImpl`，CORS 为 `allowedOrigins("*")`，且无 JUnit/前端单测与 CI。

## Goals / Non-Goals

**Goals:**

- 统一「哪些路径必须登录、哪些可匿名」的策略，并在代码与文档中一致体现。
- 消除提交到仓库的默认真实密钥与易误用的生产级危险默认项。
- 为生产环境提供可收紧的 CORS 与日志配置入口。
- 建立最小自动化验证（鉴权相关）与可扩展的 CI 骨架。

**Non-Goals:**

- 全面重写权限模型或引入新认证协议（仍使用 Sa-Token）。
- 一次性达到高测试覆盖率或完整 E2E。
- 修改 BPMN 业务语义或 Camunda 引擎行为（仅安全与配置边界）。

## Decisions

1. **鉴权策略：注解补齐为主，全局规则为辅**  
   - **选择**：为所有非白名单业务接口显式添加 `@SaCheckLogin`（及现有权限体系下的 `@SaCheckPermission` 若已有对应 permission 字符串）；在 `SaTokenConfigure` 或等价处维护少量 `SaRouter` 白名单（如 `/auth/signin`、`/auth/signout`、Swagger/OpenAPI、Actuator 若启用、静态资源等）。  
   - **理由**：与现有系统/字典等控制器风格一致，审查与代码搜索直观。  
   - **备选**：全局 `StpUtil.checkLogin()` 于除白名单外所有路径——改动面大，易误伤 Camunda REST，需更多回归。

2. **公共字典类接口**  
   - **选择**：默认要求登录（`@SaCheckLogin`），除非产品明确要求匿名；若匿名，则单独列出并写进 spec，且限制为只读、无敏感数据。  
   - **理由**：与审查结论一致，降低数据枚举风险；若业务冲突可在实现阶段与用户确认后调整 spec。

3. **密钥与配置**  
   - **选择**：根 `application.yml` 使用占位符或空值 + `spring.config.import`/`optional:application-local.yml` 或文档约定「复制 `application-local.example.yml`」；`.gitignore` 忽略 `application-local.yml`；README 说明环境变量键名（若采用 `SPRING_DATASOURCE_PASSWORD` 等）。  
   - **理由**：与 Spring Boot 惯例一致，避免仓库存密。

4. **MyBatis 日志**  
   - **选择**：默认 profile 使用 `Slf4jImpl` 或关闭 SQL 日志；仅在 `local`/`dev` profile 保留 `StdOutImpl` 可选。  
   - **理由**：避免生产 stdout 泄露 SQL 与数据。

5. **CORS**  
   - **选择**：`application.yml` 中 `app.cors.allowed-origins` 列表；开发 profile 可为 `http://localhost:4201` 等；生产通过配置注入，禁止 `*` 与凭据同时使用的不安全组合（若使用 cookie 需另行设计）。  
   - **理由**：可预测、可审计。

6. **测试与 CI**  
   - **选择**：至少一个 `MockMvc` 或 `@SpringBootTest` 用例：未带 token 访问受保护 BPM 路径应返回未登录错误码；带 token 或通过测试工具登录后可访问。CI 运行 `mvn -pl kiwi-admin/backend -am test` 与 `npm run lint`（测试可先 `|| true` 仅警告，待稳定后改为硬失败）。  
   - **理由**：最小证明鉴权生效；避免一步到位导致 CI 长期红灯。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| 收紧鉴权后，前端或第三方遗漏 token 导致 401 | 在 README 与变更说明列出「须带 Authorization 的 API」；本地 E2E 手动验一次主流程 |
| `application-local` 与团队习惯不一致 | 提供 `application-local.example.yml` 模板 |
| CI 因环境无 Mongo/MySQL 导致集成测试失败 | 鉴权测试优先使用 slice test / mock；全量集成测试标记 `@Disabled` 或 profile |
| 重命名 `geNewtProcessId` 影响外部引用 | 全局搜索后一次性重命名并编译 |

## Migration Plan

1. 在开发分支实现鉴权与配置变更；本地全量启动验证登录、BPM 设计、字典加载。  
2. 部署到测试环境：注入生产式密钥与 CORS；回归主要菜单与流程。  
3. 生产：先发布配置与代码，再轮换已泄露的密钥（若 `application.yml` 曾含真实密）。  
4. **回滚**：恢复上一镜像；配置回退；若已改密钥，保留新密钥为主。

## Open Questions

- 字典/树接口是否必须登录：当前设计默认登录；若产品要求登录页未加载前可读字典，需将对应路径加入「匿名只读」并收紧返回字段。  
- Camunda REST (`/engine-rest`) 是否由网关单独鉴权：若是，后端可保持引擎默认安全策略并文档说明，不在此变更中重复封装。
