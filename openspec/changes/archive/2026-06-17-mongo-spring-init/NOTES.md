# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/mongo_spring_init_e77aa75d.plan.md`；**阶段 1 已落地**，plan 文件已删除。

## 实现路径（以代码为准）

plan 草案为 Mongock **A（001 admin）+ B（002 单次 ChangeUnit 读 `mongo/migration/data/*.json`）**。

**实际**为双轨：

| 类型 | 机制 | 位置 |
|------|------|------|
| 命令式 | Mongock `@ChangeUnit` | `InitAdminUserChangeUnit`（`001`） |
| 参考数据 JSON | `MongoJsonMigrationRunner` + `kiwi_json_migration` changelog | `mongo/migration/versioned/`、`repeatable/`（如 `R__SysMenu.json`） |

**未实现** plan 中的 `LoadSystemReferenceDataChangeUnit`（002）；字典/菜单改由 **类 Flyway 的 JSON 脚本**（versioned 只跑一次、repeatable checksum 变更重跑）维护，见 `kiwi-admin/backend/README.md`「MongoDB 迁移」。

迁移后缓存：`MongoMigrationCacheRefresh` → `MenuService` / `DictService.refresh()`。

## 阶段 2（未做）

- cryoems 库 Mongock Runner — 仍为后续工作，见 `tasks.md`

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
