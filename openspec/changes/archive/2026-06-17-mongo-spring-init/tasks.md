# Tasks

## 阶段 1 — 主库初始化

- [x] 父 pom `mongock-bom` 5.5.1；backend `mongock-springboot-v3` + `mongodb-springdata-v4-driver`
- [x] `MongoMigrationConfiguration` + `application.yml` scan-package / `migration.enabled`
- [x] `InitAdminUserChangeUnit`（001）
- [x] JSON 加载：`ClasspathJsonMigrationSupport` + `MongoJsonMigrationRunner`（**非** plan 草案的 `002` ChangeUnit）
- [x] 参考数据：`mongo/migration/repeatable/` 与 `versioned/` 脚本（`R__SysMenu` 等）
- [x] `MongoMigrationCacheRefresh`（ApplicationReadyEvent）
- [x] `kiwi.mongodb.init.admin-*` 与 migration 配置项
- [x] `backend/README.md` Mongock + JSON 维护说明
- [x] （跳过）独立 JSON Seed 框架

## 阶段 2 — 未做

- [ ] cryoems Mongock Runner
