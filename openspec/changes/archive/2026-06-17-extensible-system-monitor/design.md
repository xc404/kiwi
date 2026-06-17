## Context

Kiwi Admin 使用 Spring Boot 与 Angular，已有统一响应封装与登录校验。原监控页为模板演示，无真实指标。本设计在不大改基础设施的前提下，引入可插拔监控聚合与类型化指标契约。

## Goals / Non-Goals

**Goals:**

- 单一快照 API，前端一次拉取多模块指标。
- 后端通过 `MonitorContributor` 扩展新模块；单模块采集失败不拖垮整次快照。
- 前端通过 `kind` 映射渲染，新增展示类型时局部扩展模板。

**Non-Goals:**

- 不做历史时序存储与告警规则引擎。
- 不替代专业 APM（如 JVM 深度剖析、分布式追踪）。

## Decisions

1. **SPI + Spring List 注入**：所有 `MonitorContributor` 以 `@Component` 注册，聚合服务按 `order` 排序。替代方案为手动在 Controller 组装列表，可维护性差。
2. **指标 `kind` 字符串契约**：与前端 `@switch` 对齐；新增类型需同步前后端。替代方案为完全动态 JSON Schema 驱动 UI，成本高。
3. **JDBC 与 Mongo 分模块**：主数据源用 `ObjectProvider<DataSource>`，Mongo 用 `ObjectProvider<MongoDatabaseFactory>`，避免可选子系统启动失败。
4. **JVM 指标使用 `com.sun.management` 扩展 MXBean**：在部分 JVM 上 CPU 采样可能短暂返回 -1，前端以 0% 展示。

## Risks / Trade-offs

- **[Risk] 进程 CPU 首次采样不准** → 接受或后续增加预热轮询。
- **[Risk] JDBC URL 脱敏不完整** → 仅掩码常见 `password=` 形态；敏感环境应依赖网关与配置外置。
- **[Risk] 轮询增加负载** → 默认 10s 间隔，可后续改为可配置。

## Migration Plan

部署新版本即可；无数据迁移。回滚即移除新 Controller 与前端页面改动。

## Open Questions

- 是否需要基于角色的监控权限（当前与多数业务接口一致为登录即可）。
