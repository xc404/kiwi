## Context

- **现状**：Kiwi 已按 `admin-mcp-openapi` 规格落地 MCP：`KiwiOpenApiSyncMcpToolsConfiguration` 扫描 `@Operation(operationId)`，`kiwiChatClient` 经本机 SSE 回环与对外 MCP 同源；`assistant_*` 仅进程内。
- **候选方案**：`infobip-openapi-mcp`（`com.infobip.openapi.mcp:infobip-openapi-mcp-spring-boot-starter`）提供完整 OpenAPI→MCP 映射、filter、mock、schema 转换等，但 README 声明基线为 **Java 21+、Spring Boot 3.5.x、Spring AI 1.1.x**。
- **Kiwi 栈**（`kiwi-admin/backend/pom.xml`）：Spring Boot **4**、Spring AI **2.0.0-M6**、Springdoc **3.0.0**。
- **历史**：`server2mcp` / `api2mcp4j` 曾因 SNAPSHOT 不可解析未接入；infobip 为下一候选，但版本门槛未满足。

## Goals / Non-Goals

**Goals:**

- 正式记录 **不接入 infobip** 直至版本门槛满足。
- 定义可执行的 **采纳门槛** 与 **PoC 验收清单**，便于未来一次性评估。
- 明确过渡期 **自研 schema 增强** 为推荐 interim，不与 infobip 等待互相阻塞。
- 建立 **定期复查** 机制（版本、Release Note、兼容性声明）。

**Non-Goals:**

- 本 change **不** 合并 infobip 依赖或替换 `KiwiOpenApiSyncMcpToolsConfiguration`。
- 本 change **不** 强制在本迭代完成自研 schema 增强（可另开 change）。
- 不评估 Sidecar（openapi-mcp 等）或 api2mcp4j，除非 infobip 长期无法满足门槛（见 Open Questions）。

## Decisions

### 1. 等待 infobip，而非降级 Kiwi BOM 去适配

- **决策**：保持 Boot 4 + Spring AI 2.x；**不** 为 infobip 单独降级平台版本。
- **理由**：平台升级成本高于等待库跟进；自研扫描已满足核心规格。
- **备选（未采纳）**：fork infobip 并自行适配 Boot 4 — 维护负担高，仅当官方长期不跟进时再考虑。

### 2. 过渡期优先「自研 schema 增强」，保留进程内直调

- **决策**：interim 在 `KiwiOpenApiSyncMcpToolsConfiguration` 内从 Springdoc OpenAPI（`/v3/api-docs` 或内存 `OpenAPI` bean）生成 `inputSchema`，执行仍用 `MethodToolCallback`。
- **理由**：与 `admin-mcp-openapi` 一致（同源、Sa-Token、`assistant_*` 隔离）；无第三方版本风险。
- **备选（未采纳）**：Sidecar HTTP 代理 — 多进程、助手回环复杂度高。

### 3. infobip 采纳门槛（Gate）

须 **同时满足** 方可开新 change 做 PoC：

| # | 门槛 | 验证方式 |
|---|------|----------|
| G1 | infobip starter 在 **Maven Central** 有稳定 release（非 SNAPSHOT） | `mvn dependency:get` |
| G2 | 官方文档或 Release Note 声明支持 **Spring Boot 4.x**（或 Kiwi 实际 BOM 版本） | README / changelog |
| G3 | 官方文档或 Release Note 声明支持 **Spring AI 2.x**（与 `spring-ai-bom` 对齐） | README / changelog |
| G4 | `mvn -pl kiwi-admin/backend -am compile -DskipTests` **在仅加 infobip 依赖的 PoC 分支通过** | CI 本地编译 |
| G5 | PoC 可保留：`assistant_*` 不进 MCP 列表、`operationId` 工具名、Sa-Token Bearer | PoC 清单（见下） |
| G6 | infobip 默认工具执行为 HTTP 时，须证明与 Kiwi **同源契约**可接受（或提供进程内/同 JVM 扩展点） | 设计评审 |

### 4. 复查节奏

- **每季度**（或 infobip 发 major/minor 时）：检查 [infobip-openapi-mcp releases](https://github.com/infobip/infobip-openapi-mcp/releases) 与 Central 坐标。
- 复查结果记入本 change 的 `NOTES.md`（日期 + 版本号 + 是否满足 G1–G3）。

### 5. infobip PoC 验收清单（门槛满足后，新 change 使用）

1. `tools/list` 工具名与现有 `operationId` 集合一致或可配置为一致。
2. 缺 `operationId`/`summary` 的端点不暴露。
3. `tools/list` 不含 `assistant_*`。
4. `kiwiChatClient` 与外部 MCP 客户端业务工具集合一致。
5. 未认证调用返回与 REST 一致的 401 语义。
6. `SPRING_AI_MCP_SERVER_ENABLED=false` 时应用正常启动。
7. 抽样 10 个工具的 `inputSchema` 与 Springdoc 一致或更完整。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| infobip 长期不支持 Boot 4 | 持续自研增强；季度复查；必要时评估 fork 或 Sidecar |
| 自研 schema 与 infobip 能力重复建设 | interim 范围控制在 schema + filter；避免复制 mock/OAuth 全家桶 |
| 等待期间 MCP 参数描述偏弱 | 优先推进自研 schema change；补 `@Parameter`/`@Schema` |
| 团队误以为「MCP 演进 = 立刻接 infobip」 | 本 OpenSpec + 路线图 NOTES 明示 gate |

## Migration Plan

本 change **无生产迁移**。

当未来 infobip PoC 通过且评审采纳时：

1. 新建 change（如 `admin-mcp-adopt-infobip`），非在本 change 内直接改代码。
2. PoC 分支验证 G4–G6 与验收清单。
3. 逐步替换或并存 `KiwiOpenApiSyncMcpToolsConfiguration`（以 PoC 设计为准）。
4. 归档本 change，在 `NOTES.md` 记录触发版本与结论。

回滚：不引入 infobip 则无回滚成本；若已引入则 revert 依赖并恢复自研配置 Bean。

## Open Questions

- infobip 是否计划支持 Spring Boot 4 / Spring AI 2？（可向 upstream issue 跟踪）
- infobip 在 Kiwi 场景下是否必须 HTTP 执行，还是可插拔为 `MethodToolCallback`？
- 若 2026 Q4 仍不满足 G2/G3，是否将 api2mcp4j（Central 发布后）提升为第二候选？
