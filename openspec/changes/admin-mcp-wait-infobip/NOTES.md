# 归档 / 跟踪说明

## 基线复查 — 2026-06-17

| 门槛 | 状态 | 说明 |
|------|------|------|
| G1 Central 非 SNAPSHOT | 部分 | `0.1.16` 在 Central；artifact 本身为 release |
| G2 Spring Boot 4.x | ❌ | README：Spring Boot **3.5.x** |
| G3 Spring AI 2.x | ❌ | README：Spring AI **1.1.x** |
| G4 Kiwi 编译通过 | 未测 | 未引入依赖；预期 BOM 冲突 |
| G5 Kiwi 硬约束 PoC | 未测 | 等待 G2/G3 |
| G6 执行路径可接受 | 未测 | infobip 默认 HTTP 代理，需 PoC 评审 |

**结论**：维持等待；业务 MCP 继续 `KiwiOpenApiSyncMcpToolsConfiguration` + Spring AI MCP Server SSE 回环。

**Kiwi 栈参考**：`kiwi-admin/backend/pom.xml` — Spring Boot 4、Spring AI 2.0.0-M6、Springdoc 3.0.0。

**上游**：https://github.com/infobip/infobip-openapi-mcp

---

## 复查记录模板（后续条目复制此表）

### YYYY-MM-DD

| 门槛 | 状态 | 说明 |
|------|------|------|
| G1 | | |
| G2 | | |
| G3 | | |
| 结论 | | |

---

## 关联

- 已归档：`openspec/changes/archive/2026-06-17-swagger-mcp-plug-server2mcp-webmvc`
- 现行规格：`openspec/specs/admin-mcp-openapi/spec.md`
- 路线图：`.cursor/plans/kiwi_optimization_roadmap_099c022a.plan.md`（MCP 演进）
