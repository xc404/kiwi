# kiwi-admin backend

## MongoDB 迁移

启动时在 `kiwi.mongodb.migration.enabled=true`（默认）下执行两类迁移：

| 类型 | 机制 | 适用 |
|------|------|------|
| **命令式** | [Mongock](https://docs.mongock.io/) `@ChangeUnit`（如 `InitAdminUserChangeUnit`） | 密码哈希、复杂逻辑 |
| **参考数据 JSON** | `MongoJsonMigrationRunner` 扫描 classpath，changelog 集合 `kiwi_json_migration` | 字典、菜单等实体 upsert |

### JSON 脚本约定（类 Flyway）

- **版本化（只执行一次）**：`src/main/resources/mongo/migration/versioned/`  
  文件名：`V{version}__{EntitySimpleName}.json`  
  示例：`V20250601_001__SysDictGroup.json` → 实体 `com.kiwi.project.system.entity.SysDictGroup`
- **可重复（checksum 变化时重跑）**：`mongo/migration/repeatable/`  
  文件名：`R__{EntitySimpleName}.json`  
  示例：`R__SysMenu.json`

JSON 为**对象数组**，每项须含非空 `id`。新增参考数据时**只加文件**，无需新建 Java ChangeUnit；版本化脚本通过提高 `V` 前缀版本保证顺序。

### 配置

```yaml
kiwi.mongodb.migration.enabled: true
kiwi.mongodb.migration.json.entity-base-package: com.kiwi.project.system.entity
kiwi.mongodb.init.admin-password: # 001 管理员迁移必填
```

环境变量（relaxed binding）：`KIWI_MONGODB_INIT_ADMIN_USERNAME`、`KIWI_MONGODB_INIT_ADMIN_PASSWORD`、`KIWI_MONGODB_INIT_ADMIN_NICK_NAME`；`application.yml` 中仍兼容 `KIWI_INIT_ADMIN_*` 占位符。

关闭迁移：`KIWI_MONGODB_MIGRATION_ENABLED=false`。

Mongock 事务：默认 `mongock.transactional=false`（`MONGOCK_TRANSACTIONAL`）。本地单机 Mongo 或同时启用 MySQL/H2 与 Mongo 时请勿依赖自动推断；仅在 Mongo **副本集**且确需迁移原子性时设为 `true`。

**常见启动错误**（`Transaction numbers are only allowed on a replica set` / `Error in Mongock's transaction`）：说明 Mongock 仍在对**单机** Mongo 开事务。确认 `application.yml` 含 `mongock.transactional: false` 且已重新打包；若用 `deploy` 或 `bin/restart.sh` 启动，需重新同步 `bin/config/`（`deploy.py` 会从 `src/main/resources` 复制配置）。

### 验证

1. 空库启动后：`mongockChangeLog` 含 `001`；`kiwi_json_migration` 含 versioned/repeatable 脚本记录。  
2. 修改 `R__SysMenu.json` 后重启：菜单数据更新且 changelog 中 checksum 变化。  
3. 新增 `V20250602_001__SysRole.json`：仅新脚本执行一次。
