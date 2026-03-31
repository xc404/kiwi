## Context

- 后端统一响应经 `ResponseAdvice` 包装；`List` 返回体会变为带 `content` 的集合结构，前端 `BaseHttpService.get` 解包后 `data` 为 `{ content: T[] }`。
- 会话用户 ID 来自 `BaseCtl.getCurrentUserId()`（Sa-Token `StpUtil`）。
- 前端已有个人中心懒加载路由模式，消息页复用同一 `personal` 子路由。

## Goals / Non-Goals

**Goals:**

- 每用户独立消息列表；铃铛下拉仅展示轻量预览，全量在独立页。
- 与现有 Angular 独立组件 + `inject(BaseHttpService)` 风格一致。

**Non-Goals:**

- 本次不实现服务端「已读/删除」持久化（页面侧仍可本地更新 UI，待后续 API）。
- 不实现 WebSocket / SSE 实时推送。

## Decisions

1. **存储**：使用 MongoDB 文档 `notification_message`，扩展 `BaseMongoRepository`，避免引入 MySQL 迁移。
2. **种子数据**：`countByUserId == 0` 时插入固定示例数据，便于演示与联调；生产可替换为异步写入或关闭。
3. **ID**：主键使用 `"{业务键}-{userId}"` 避免跨用户冲突。
4. **DTO 字段**：`createdAt` 使用 ISO-8601 字符串；可选 `tag` 映射自 `tagText`/`tagColor`。

## Risks / Trade-offs

- **[Risk] 种子数据写入** → 仅首访触发；若需清空需运维删集合或按用户删文档。
- **[Risk] 布局组件依赖 pages 下 Service** → 可后续抽到 `core/services` 降低耦合。

## Migration Plan

- 新集合自动创建；无数据库迁移脚本；回滚可删除 `notification_message` 集合与相关 Java 包。

## Open Questions

- 是否将「已读/删除」持久化纳入下一变更（与 `PUT`/`DELETE` 设计）。
