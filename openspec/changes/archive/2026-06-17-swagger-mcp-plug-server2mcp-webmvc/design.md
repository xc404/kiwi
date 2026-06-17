## Context

- **现状**：`kiwi-admin` 使用 `spring-ai-starter-mcp-server-webmvc`，通过 `KiwiToolCallbackConfiguration` 扫描带 `@Tool` 的方法构建 `MethodToolCallbackProvider`，与 Spring AI MCP Server 及 `ChatClient` 共用同一批回调。**目标态**：对外 MCP 与 `ChatClient` 均以 **server2mcp 从 OpenAPI 推导的能力** 为单一工具面（见决策 2）。
- **痛点**：大量控制器方法手写 `@Tool(name=..., description=...)`，与 `springdoc-openapi` 已生成的 REST 文档重复；新增或改 HTTP 接口时易遗漏 MCP 侧同步。
- **目标技术**：`com.ai.plug:server2mcp-starter-webmvc`（用户所述坐标；若中央仓不可用则采用构建/私服同源版本）从 Spring Web + OpenAPI 元数据生成 MCP 工具/资源，减少对 `@Tool` 的依赖。

## Goals / Non-Goals

**Goals:**

- 对外 MCP 服务能力以 **OpenAPI 3（Springdoc）** 为主数据源，由 server2mcp starter 完成与 HTTP 控制器的映射暴露。
- 从代码库中 **移除面向 MCP 的 `@Tool` 标注**（及仅为此存在的包装方法），改为依赖标准 MVC + Swagger 3 注解；对描述不足的端点 **补齐 `@Tag` / `@Operation` / `@Parameter` 等**。
- 明确 **认证与授权**：与现有 API 一致（如 `Authorization: Bearer` + Sa-Token），并在 MCP 调用路径上可验证。
- 在 `application.yml`（或等价配置）中给出 **可运维开关与环境变量**，文档化 **BREAKING** 的 URL/协议差异（相对原 Spring AI MCP Server 默认路径）。

**Non-Goals:**

- 不强制在本变更中重写全部业务域逻辑；仅调整「如何暴露 MCP」与文档注解。
- 不引入替代 Chat 框架；助手仍以 Spring AI Alibaba `ChatClient` 为主，**工具面**与 server2mcp 对齐（见决策 2）。

## Decisions

1. **MCP 运行时**：采用 `server2mcp-starter-webmvc` 作为 MCP 对外入口；**移除** `spring-ai-starter-mcp-server-webmvc` 依赖及 `spring.ai.mcp.server.*` 中与 Spring AI 自带 MCP Server 重复的配置，避免两套 MCP 栈并存冲突。  
   - *备选（未采纳）*：双栈并存 — 增加运维与端口/路径混淆，仅当迁移期极短时考虑。

2. **`ChatClient` 工具来源**（**已采纳**）：`ChatClient` **SHALL** 与 server2mcp 暴露的业务能力一致，采用 **同源集成**——优先使用 starter 文档中的 **Spring AI / `ToolCallback` 桥接**；若无现成桥接，则从 **与 server2mcp 相同的 OpenAPI 文档**（如 Springdoc 产出的 JSON）生成或包装为 `ToolCallback`，注册到 `ChatClient`，**禁止**再使用 `KiwiToolCallbackConfiguration` 式「扫描全容器 `@Tool`」作为助手工具来源。  
   - 原 **备选 A**（白名单 `@Tool` + 其余走 HTTP）**不采纳**。  
   - 非 REST 的纯助手能力（如仅 UI 导航）：**SHALL** 改为 REST 端点 + OpenAPI 描述并纳入同一扫描范围，或纳入 starter 支持的 prompt/resource 等机制，**不得**单独挂 `@Tool` 绕过 OpenAPI 面。

3. **OpenAPI 质量**：以 **springdoc 默认 + 显式注解** 为准；对无方法级摘要的控制器方法 **MUST** 补 `@Operation(summary = ..., description = ...)`；复杂路径/查询参数 **MUST** 有 `@Parameter` 或与 DTO 字段上的 `@Schema` 组合可读。类级 **MUST** 有 `@Tag`（或分组等价物），便于 MCP 工具分组命名。

4. **暴露范围**：使用 starter 文档中的 **扫描/包含/排除** 机制（如 `@ToolScan` 包路径、或配置属性）限定仅管理后台 API 包（如 `com.kiwi.project.*.ctl`），**排除** Camunda 引擎 REST、Actuator（若存在）等非本应用 OpenAPI 管理的入口，除非产品明确要求。

5. **依赖获取**：若 `com.ai.plug:server2mcp-starter-webmvc` 不在 Maven Central，则在父/子 POM 或 README 中增加 **私服坐标或 `mvn install` 源码构建** 步骤；CI 需能解析该构件。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| starter 与 Spring Boot 3.5 / Spring Framework BOM 版本冲突 | 在 PoC 分支先跑通 `mvn -pl kiwi-admin test` 与启动；必要时 pin starter 版本或向维护方提 issue |
| 移除 Spring AI MCP Server 后外部已有集成失效 | 在 proposal/发布说明中标注 **BREAKING**；提供迁移对照表（旧路径 → 新路径） |
| 纯 OpenAPI 无法表达流式/文件上传等 MCP 语义 | 非目标端点可暂时不暴露为 MCP，或保留窄范围自定义扩展（后续 change） |
| starter 无官方 `ToolCallback` 桥接、需自研 OpenAPI→回调 | PoC 阶段验证 API；必要时薄封装层调用内部 `RestTemplate`/`WebClient` 执行同源路径，仍由 OpenAPI 定义契约 |

## Migration Plan

1. 分支引入依赖与最小配置，验证 OpenAPI JSON 可被 starter 消费并启动 MCP。
2. 分批删除控制器 `@Tool`，每批跑回归（重点：原 MCP 覆盖的读/写路径）。
3. 更新部署与环境变量文档；通知 MCP 客户端维护者切换连接方式。
4. 回滚：保留 Git revert；若双栈曾短暂存在，回滚即恢复 `spring-ai-starter-mcp-server-webmvc` 与 `@Tool` 提交。

## Open Questions

- `com.ai.plug:server2mcp-starter-webmvc` 的 **确切 Maven 坐标与稳定版本号**（是否 Central、是否与用户写的是同一 artifact）。
- server2mcp 是否支持 **SSE/Streamable HTTP** 等与当前客户端假设一致的传输方式。
- starter **内置 Spring AI 桥接的具体类名与配置键**（以实现 `ChatClient` 注册为准，实现前对照官方文档或示例仓库）。
