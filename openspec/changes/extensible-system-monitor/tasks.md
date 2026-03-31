## 1. 后端可扩展监控

- [x] 1.1 定义 DTO 与 `MonitorContributor`、`MonitorAggregationService`、`MonitorCtl`（`GET /monitor/snapshot`）
- [x] 1.2 实现 JVM/系统、`JdbcDataSource`、`MongoDb` 三类默认贡献者

## 2. 前端监控页

- [x] 2.1 新增 `MonitorService` 与类型模型，消费快照 API
- [x] 2.2 重写 `MonitorComponent`：按 `kind` 渲染、轮询与手动刷新

## 3. 文档与 OpenSpec

- [x] 3.1 撰写本变更的 proposal、design、spec、tasks
