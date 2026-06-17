## ADDED Requirements

### Requirement: infobip 采纳版本门槛

在 infobip-openapi-mcp **同时**满足以下条件之前，Kiwi 管理后台 SHALL NOT 在 `kiwi-admin/backend` 中引入 `com.infobip.openapi.mcp` 依赖，SHALL NOT 用其替换 `KiwiOpenApiSyncMcpToolsConfiguration` 作为业务 MCP 工具注册主路径：

1. Maven Central 存在**非 SNAPSHOT** 的 release 构件；
2. 官方文档或 Release Note 声明支持 Kiwi 当前使用的 **Spring Boot 4.x**（或与之对齐的 BOM）；
3. 官方文档或 Release Note 声明支持 Kiwi 当前使用的 **Spring AI 2.x**（与 `spring-ai-bom` 版本一致或兼容）。

#### Scenario: 门槛未满足时不添加依赖

- **WHEN** infobip 仅支持 Spring Boot 3.5 / Spring AI 1.1 或仍为 SNAPSHOT
- **THEN** `kiwi-admin/backend/pom.xml` SHALL 不包含 `com.infobip.openapi.mcp` 依赖
- **THEN** 业务 MCP 工具 SHALL 继续由 `KiwiOpenApiSyncMcpToolsConfiguration` 注册

### Requirement: 定期复查 infobip 版本

项目 SHALL 至少每季度或在 infobip 发布 major/minor 版本时，复查其 Release Note 与 Maven Central 坐标是否满足采纳版本门槛，并将结论记录于 `openspec/changes/admin-mcp-wait-infobip/NOTES.md`（含日期与版本号）。

#### Scenario: 复查记录可追溯

- **WHEN** 维护者完成一次 infobip 版本复查
- **THEN** `NOTES.md` SHALL 新增一条记录，说明所查版本号及 G1–G3 门槛是否满足

### Requirement: 门槛满足后的 PoC 变更隔离

当 infobip 满足版本门槛时，集成与迁移工作 SHALL 在**新的 OpenSpec change** 中开展（不得在本 wait change 内直接合并生产代码），且 PoC SHALL 通过 design.md 所列验收清单后方可替换现有 MCP 注册实现。

#### Scenario: 新版本触发独立 change

- **WHEN** infobip release 满足 Spring Boot 4 与 Spring AI 2.x 声明且 Central 可解析
- **THEN** 团队 SHALL 创建独立 change（如 `admin-mcp-adopt-infobip`）进行 PoC
- **THEN** 在未通过 PoC 验收前 SHALL 保留 `KiwiOpenApiSyncMcpToolsConfiguration` 为生产路径
