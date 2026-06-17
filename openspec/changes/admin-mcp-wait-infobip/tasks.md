## 1. 文档与决策落盘

- [x] 1.1 编写 `proposal.md`：明确等待 infobip、不降级 Kiwi BOM
- [x] 1.2 编写 `design.md`：采纳门槛 G1–G6、PoC 验收清单、复查节奏
- [x] 1.3 编写 `specs/admin-mcp-infobip-gate/spec.md` 与 `specs/admin-mcp-openapi/spec.md` delta
- [x] 1.4 创建 `NOTES.md` 并记录基线复查（2026-06-17）

## 2. 基线复查（2026-06-17）

- [x] 2.1 确认 infobip README 基线：Spring Boot 3.5.x、Spring AI 1.1.x（与 Kiwi Boot 4 / AI 2.0-M6 不符）
- [x] 2.2 确认 Central 坐标 `com.infobip.openapi.mcp:infobip-openapi-mcp-spring-boot-starter:0.1.16` 存在，但版本声明未覆盖 Kiwi 栈
- [x] 2.3 结论：**门槛 G2/G3 未满足**，维持自研 `KiwiOpenApiSyncMcpToolsConfiguration`

## 3. 持续跟踪（门槛满足前）

- [ ] 3.1 下一季度复查 infobip Release Note / README（目标：2026-09 前）
- [ ] 3.2 若 infobip 声明 Boot 4 + AI 2.x，执行 `mvn dependency:get` 验证 G1 并更新 `NOTES.md`
- [ ] 3.3 门槛满足时新建 change `admin-mcp-adopt-infobip`（或等价名称），按 design PoC 清单评估

## 4. 过渡期（可选，独立 change）

- [ ] 4.1 若需提升 MCP `inputSchema`，另开 change 实现 Springdoc 驱动 schema（不依赖 infobip）
- [ ] 4.2 补全控制器 `@Parameter` / `@Schema` 以改善 OpenAPI 与 MCP 描述一致性

## 5. 归档前

- [ ] 5.1 在 `kiwi_optimization_roadmap` 或相关 plan 中引用本 change（MCP 演进 = 等待 infobip + 可选自研 schema）
- [ ] 5.2 门槛长期不满足时，在 `NOTES.md` 记录是否提升 api2mcp4j 为第二候选（见 design Open Questions）
