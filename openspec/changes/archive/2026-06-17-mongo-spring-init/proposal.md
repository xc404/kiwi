## Why

空库首启时缺少 admin 用户与系统字典/菜单参考数据，且 `MenuService` / `DictService` 在 `afterPropertiesSet` 缓存数据——若迁移晚于 Bean 初始化，前端首启菜单为空直至重启。需要可重复、可审计的 Mongo 初始化机制。

## What Changes

- 引入 **Mongock** 执行命令式迁移（`001` 初始化 admin，`PasswordService` 哈希）
- 引入 **classpath JSON 参考数据**迁移（字典、菜单等 upsert）
- `ApplicationReadyEvent` 后刷新 Menu/Dict 内存缓存
- 配置 `kiwi.mongodb.migration.enabled`、`kiwi.mongodb.init.admin-*`

## Capabilities

### New Capabilities

- （无 main spec。）

### Modified Capabilities

- （无。）

## Impact

- `pom.xml`（mongock-bom）、`kiwi-admin/backend` Mongock 依赖
- `com.kiwi.framework.mongo.migration.*`
- `application.yml`、`backend/README.md`

## 非目标（阶段 1）

- cryoems Seed、独立 ApplicationReadyEvent JSON Seed
- cryoems Mongock（阶段 2）
- 索引进 ChangeUnit（仍用 `@Indexed` + auto-index）
