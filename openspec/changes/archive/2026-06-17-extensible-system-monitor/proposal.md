## Why

Dashboard 监控页原为演示用图表与地图占位，无法反映 Kiwi Admin 运行时的 CPU、内存与数据存储健康度。运维与开发需要一处可扩展的监控入口，并能随业务增加新的观测维度，而无需在前后端硬编码零散接口。

## What Changes

- 后端提供统一快照 API，聚合多类监控模块；通过 `MonitorContributor` SPI 注册新模块（如 JVM/系统、JDBC、MongoDB 等）。
- 前端监控页改为消费该快照，按指标 `kind` 渲染（percent / number / bytes / boolean / text），并支持定时轮询与手动刷新。
- 移除监控页对 AMap、G2Plot 演示图表的依赖，降低无关复杂度。

## Capabilities

### New Capabilities

- `admin-runtime-monitor`: 已登录用户可获取运行时监控快照；模块与指标结构稳定、可扩展，新增监控源通过后端贡献者 Bean 与前端 `kind` 分支扩展。

### Modified Capabilities

- （无）未修改 `openspec/specs/` 下既有规范；本变更为新增能力。

## Impact

- **后端**：`com.kiwi.project.monitor` 包、`GET /monitor/snapshot`（需登录）。
- **前端**：`pages/dashboard/monitor/*`、`MonitorService`。
- **依赖**：无新增 Maven/npm 依赖。
