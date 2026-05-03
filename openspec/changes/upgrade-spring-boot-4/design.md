## Context

- Kiwi 使用 **JDK 25**（根 POM enforcer 强制），当前 **Spring Boot 3.5.8**，并显式导入 **Spring Framework 6.2.14** BOM 以避免传递依赖将 `spring-core` 锁到过旧版本导致 JDK 25 字节码解析失败。
- 后端聚合 **Camunda 7**（`camunda-bpm-spring-boot-starter*`）、**Spring AI / Alibaba**、**sa-token**、**MongoDB**、**mica** 等；`cyroems` 使用独立 `spring-boot-starter-parent` 需与主仓库对齐。
- 升级到 **Spring Boot 4.0.x** 属于跨模块变更，需遵循 Camunda 官方对 **Spring Boot 4 专用 `-4` 工件** 与最低补丁版本的要求。

## Goals / Non-Goals

**Goals:**

- 全仓库 Spring Boot 版本与 BOM 策略统一到 **4.0.x** 选定补丁。
- Camunda 集成迁移到官方支持的 **`-4` starter** 与 **≥ 7.24.3** 的 Camunda 7 线（与 Camunda 文档一致）。
- Spring Framework 以 Boot 4 管理的 **7.x** 为主；验证通过后移除不再需要的 6.2.x 覆盖。
- 关键第三方依赖在 Boot 4 下可解析、可启动、核心集成测试或手工冒烟通过。

**Non-Goals:**

- 不改变业务功能需求（除因 Boot 4 废弃 API 被迫替换的等价实现外）。
- 不在本变更内升级 **Camunda 8** 或替换 BPM 引擎。
- 不在此变更中规定前端 Angular 版本。

## Decisions

1. **目标 Boot 线**：采用 **Spring Boot 4.0.x** 当前稳定补丁（实现时取 release notes 与 Maven Central 最新修复版），避免依赖 **4.1 RC** 除非团队明确接受预发布依赖。
2. **Camunda**：采用 **`camunda-bpm-spring-boot-starter-4`**（及 `webapp`/`rest`/external-task 的 **`-4`** 对应物），版本 **≥ 7.24.3**，与 [Camunda Spring Boot 兼容表](https://docs.camunda.org/manual/latest/user-guide/spring-boot-integration/version-compatibility/) 一致；社区版坐标以 Camunda 文档为准（非 `-ee` 后缀若适用）。
3. **Spring Framework BOM**：删除根 POM 中为 Boot 3 引入的 `spring-framework-bom` **6.2.x** `import`，除非集成测试发现某传递依赖再次压低 Framework 版本导致 JDK 25 类解析失败——此时仅在记录原因的前提下添加 **7.x** 对齐覆盖。
4. **第三方核验顺序**：先 **Camunda + Spring AI（Alibaba BOM / MCP starter）**（最易阻塞），再 **sa-token、springdoc、mica、mybatis-plus**；每步 `dependency:tree` 与一次最小启动验证。
5. **迁移文档**：以 Spring Boot 4 [迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)（及正式版 wiki）为清单，逐项勾选属性重命名、废弃类替换。

**Alternatives considered**

- **停留在 Boot 3.5 仅升补丁**：风险更低，但不满足本变更目标；可作为 rollback 分支保留。
- **同时跳 Spring AI 大版本**：复杂度高；仅在 BOM 要求时联动升级，并写入 tasks 验证项。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| spring-ai-alibaba 或 MCP starter 尚未稳定支持 Boot 4 | 升级前查对应 BOM 发行说明；必要时暂缓 AI 依赖版本或拆分子模块验证 |
| Camunda `-4` 与 REST/Webapp 组合遗漏某一工件 | 对照 Camunda 文档列全 starter 清单，启动后访问 `/camunda` 与 REST 探活 |
| 移除 Framework 6.2 覆盖后旧依赖冲突再现 | 保留 Git 回滚点；用 `mvn dependency:tree` 检查 `spring-core` 版本 |
| 配置属性前缀在 Boot 4 变更导致静默行为差异 | 对照迁移指南 grep `application*.yml`，并对关键配置做集成测试 |

## Migration Plan

1. 分支上升级父 POM 与子模块版本属性；替换 Camunda 坐标；调整 Framework BOM。
2. 本地 **`mvn -q verify`**（或模块增量构建）；修复编译与测试。
3. 启动 `kiwi-admin` 后端与（若适用）`cyroems`，冒烟 BPM、登录、OpenAPI、AI 调用路径。
4. 合并前 CI 全绿；发布说明中注明 **BREAKING** 与运维需知的 JVM/镜像变更。

**Rollback**：恢复前一 Git 提交或标签；数据库与 Camunda 引擎本变更通常无 schema 强制迁移（除非 Camunda 小版本说明要求）。

## Open Questions

- 目标 **Spring Boot 4.0.x** 的确切补丁号（以实施当周 Central 与团队审批为准）。
- **spring-ai-alibaba** 与 **`spring-ai-starter-mcp-server-webmvc`** 在实施时应锁定的兼容版本组合（以实现时仓库 BOM 为准）。
