# kiwi-admin backend

Spring Boot 主应用：REST API、嵌入式 **Operaton 2.x** BPM 引擎、系统管理（用户/菜单/字典等）、BPM 项目与流程、AI/MCP、通知与监控。与 [frontend](../frontend/README.md) 通过 HTTP 协作；平台总览见仓库根 [README.zh-CN.md](../../README.zh-CN.md)（[English](../../README.md)）。

**Camunda 7 回滚**：检出 Git 标签/分支 **`camunda`** 可回到迁移前 Camunda 7.24 + Boot 3.5 代码基线；引擎库须用迁移前备份恢复。

## 代码结构

```
src/main/java/com/kiwi/
├── framework/          # 横切与基础设施
│   ├── springboot/     # Application 入口、自动配置
│   ├── mongo/          # Mongock、JSON 迁移
│   ├── error/          # 全局异常与统一响应
│   └── …               # 安全、Sa-Token、Web 等
└── project/            # 业务域
    ├── system/         # 用户、角色、菜单、部门、字典
    ├── bpm/            # 流程定义、项目、组件元数据
    ├── ai/             # Spring AI 对话与助手
    ├── tools/          # 代码生成、JDBC 等
    ├── monitor/
    └── notification/
```

资源与配置：`src/main/resources/application*.yml`、`mongo/migration/`（JSON 参考数据）。

## 配置与 Profile

| Profile | 典型用途 |
|---------|----------|
| `local` | 加载 `application-local.yml`（连接串、密钥；文件已 `.gitignore`） |
| `dev` | Operaton 引擎库使用 H2（`./data/dev-bpm`）、端口 **8000**、MyBatis StdOut 等 |
| `redis` | Sa-Token 存 Redis（需 `application-redis.yml` + Redis 连接） |

本地推荐启动参数：

```text
--spring.profiles.active=local,dev
```

复制并按环境修改模板：

```bash
cp src/main/resources/application.example.yml src/main/resources/application-local.yml
```

未激活 `dev` 时，默认 `application.yml` 中 `server.port` 为 **8080**；`dev` profile 覆盖为 **8000**。前端 `environment.ts` 的 `port` 须与当前生效端口一致。

### 常用环境变量

| 变量 | 说明 |
|------|------|
| `SPRING_DATA_MONGODB_URI` | 主 MongoDB |
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | Operaton/MyBatis 关系库（非 `dev`） |
| `OPERATON_ADMIN_USER` / `OPERATON_ADMIN_PASSWORD` | Operaton Webapp/Tasklist 演示管理员（原 `CAMUNDA_*`） |
| `KIWI_MONGODB_MIGRATION_ENABLED` | 是否执行 Mongo 迁移，默认 `true` |
| `KIWI_INIT_ADMIN_PASSWORD` / `kiwi.mongodb.init.admin-password` | 首次管理员密码（迁移必填） |
| `KIWI_MONGODB_INIT_ADMIN_USERNAME` / `KIWI_MONGODB_INIT_ADMIN_NICK_NAME` | 管理员用户名、昵称 |
| `APP_CORS_ALLOWED_ORIGINS` | 允许的前端 Origin（逗号分隔） |
| `APP_PASSWORD_SECRET` | 密码哈希密钥 |
| `KIWI_AI_API_KEY` / `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `KIWI_AI_ENABLED` | 是否启用 AI，默认 `true` |
| `KIWI_SA_TOKEN_STORAGE` | `mongodb`（默认）或 `redis` |
| `MONGOCK_TRANSACTIONAL` | Mongock 事务，本地单机 Mongo 保持 `false` |

完整键名与默认值见 [application.yml](src/main/resources/application.yml)、[application.example.yml](src/main/resources/application.example.yml)。

## 本地启动

1. 准备 MongoDB（及非 `dev` 时的 MySQL）。
2. 配置 `application-local.yml`（含 `kiwi.mongodb.init.admin-password`、`app.password.secret` 等）。
3. 在**仓库根目录**编译依赖模块：

   ```bash
   mvn -pl kiwi-admin/backend -am compile -DskipTests
   ```

4. IDE 运行 `com.kiwi.framework.springboot.Application`，Profile `local,dev`。
5. 默认（`local,dev`）API：**http://localhost:8000**；引擎 REST：**/engine-rest**；Swagger 路径见启动日志。

### Operaton 依赖与 Maven Central

Operaton **2.1.x** 发布在 [Maven Central](https://repo1.maven.org/maven2/org/operaton/)。若私服/阿里云镜像未同步，根 `pom.xml` 已声明仓库 `operaton-maven-central`；仍失败时可执行 `mvn -U` 并清理 `~/.m2/repository/org/operaton` 中损坏缓存。

配置前缀为 **`operaton.bpm.*`**（替代原 `camunda.bpm.*`）。引擎库自 Camunda 7.24 升级前请 **备份** 关系库。

首次启动且迁移开启时会写入管理员与菜单/字典等参考数据。

### CORS

`app.cors.allowed-origins` 默认包含 `http://localhost:4201`。自定义前端地址时设置 `APP_CORS_ALLOWED_ORIGINS`。

### 打包

```bash
# 仓库根目录
mvn -pl kiwi-admin/backend -am package -DskipTests
```

远程部署见 [deploy/README.md](deploy/README.md)。

## MongoDB 迁移

`kiwi.mongodb.migration.enabled=true`（默认）时执行两类迁移：

| 类型 | 机制 | 适用 |
|------|------|------|
| **命令式** | [Mongock](https://docs.mongock.io/) `@ChangeUnit`（如 `InitAdminUserChangeUnit`） | 密码哈希、复杂逻辑 |
| **参考数据 JSON** | classpath 扫描，changelog 集合 `kiwi_json_migration` | 字典、菜单等 upsert |

### JSON 脚本约定（类 Flyway）

- **版本化（只执行一次）**：`src/main/resources/mongo/migration/versioned/`  
  文件名：`V{version}__{EntitySimpleName}.json`  
  示例：`V20250601_001__SysDictGroup.json`
- **可重复（checksum 变化时重跑）**：`mongo/migration/repeatable/`  
  文件名：`R__{EntitySimpleName}.json`  
  示例：`R__SysMenu.json`

JSON 为**对象数组**，每项须含非空 `id`。新增参考数据时**只加文件**；版本化脚本通过提高 `V` 前缀保证顺序。

### 从开发库导出参考数据

1. 在已配置好的环境用 `mongosh` 查询集合并复制为 JSON 数组；或  
2. 手写最小集：覆盖前端路由所需菜单（如 `menu`、`user`、`role`、`dict`）及常用 `groupCode`。

```javascript
// mongosh（库名按环境修改）
const docs = db.sysMenu.find().toArray();
// 保存为 R__SysMenu.json，保留 id、parentId、path、menuType、status 等字段
```

### 迁移配置与排错

```yaml
kiwi.mongodb.migration.enabled: true
kiwi.mongodb.init.admin-password: # 001 管理员迁移必填
```

关闭迁移：`KIWI_MONGODB_MIGRATION_ENABLED=false`。

Mongock 事务：默认 `mongock.transactional=false`。单机 Mongo 勿设为 `true`；若出现 `Transaction numbers are only allowed on a replica set`，确认配置已生效并重新打包；`deploy` 启动时需同步 `bin/config/`。

### 验证

1. 空库启动：`mongockChangeLog` 含 `001`；`kiwi_json_migration` 有 versioned/repeatable 记录。  
2. 修改 `R__SysMenu.json` 后重启：菜单更新且 checksum 变化。  
3. 新增 `V20250602_001__SysRole.json`：仅新脚本执行一次。

## 相关文档

- [../../README.zh-CN.md](../../README.zh-CN.md) — 仓库总览与目录 Map（[English](../../README.md)）  
- [../frontend/README.md](../frontend/README.md) — 前端配置与联调  
- [deploy/README.md](deploy/README.md) — 远程部署脚本  
