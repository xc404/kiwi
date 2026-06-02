# Kiwi

**Kiwi** 是基于 [Camunda BPM 7](https://camunda.com/platform/) 的工作流编排与管理平台，提供可视化 BPMN 设计、可扩展流程组件、系统管理后台，以及与 CryoEMS 等业务系统的集成能力。仓库为 Maven 多模块 monorepo，前后端分离部署。

## 在线演示

管理后台演示环境：[https://www.kiwi-admin.cn](https://www.kiwi-admin.cn)

## 特性

- **BPMN 流程设计**：Angular + BPMN.js 可视化建模，属性面板与组件元数据联动
- **Camunda 引擎**：流程定义/实例管理、REST API（`/engine-rest`）、External Task、异步 Job 与可配置重试
- **可插拔流程组件**：Shell、HTTP、文件读写、变量赋值、MongoDB、Slurm 等 JavaDelegate / External Task
- **管理后台**：用户、角色、菜单、部门、字典；Sa-Token 鉴权；Personal Access Token
- **低代码工具**：代码生成、JDBC 连接与表结构浏览
- **AI 辅助**：Spring AI（通义 DashScope）对话；内置 MCP Server，支持页面导航与 BPM 设计器编排
- **数据迁移**：Mongock 命令式迁移 + JSON 参考数据（类 Flyway 约定）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java **25**、Spring Boot **3.5**、Camunda **7.24**、MongoDB、MyBatis、Sa-Token |
| 前端 | Angular **21**、ng-zorro-antd、BPMN.js、ECharts、@antv/x6 |
| 构建 | Maven（多模块）、npm |
| 规格 | [OpenSpec](openspec/)（`spec-driven` 工作流） |

## 仓库结构

```
kiwi/
├── kiwi-common/              # 公共实体与工具
├── kiwi-bpmn/
│   ├── kiwi-bpmn-core/       # 组件注解、变量映射、Job 重试等引擎扩展
│   ├── kiwi-bpmn-component/  # 内置流程组件（Shell、HTTP、Slurm…）
│   └── kiwi-bpmn-external-task/  # External Task 抽象与重试
├── kiwi-admin/
│   ├── backend/              # Spring Boot 主应用（API + Camunda）
│   └── frontend/             # Angular 管理端
├── openspec/                 # 变更规格与任务（OpenSpec）
└── docs/                     # 补充文档（如 Maven settings 片段）
```

## 环境要求

| 依赖 | 说明 |
|------|------|
| **JDK 25** | 根 `pom.xml` 通过 `maven-enforcer-plugin` 强制 `[25,26)`；`JAVA_HOME` 须指向 JDK 25 |
| **Maven 3.x** | 在仓库根目录执行构建 |
| **MongoDB** | 系统数据、Sa-Token（默认）、菜单/字典等 |
| **MySQL** | 生产/联调 Camunda 引擎库（`spring.datasource`） |
| **Node.js** | 前端开发；版本需与 Angular 21 兼容 |

开发 profile `dev` 下 Camunda 可使用内嵌 **H2** 文件库（`./data/dev-bpm`），但仍需可用的 MongoDB。

## 快速开始

### 1. 后端

1. **本地配置**（推荐）：复制并按环境修改

   ```bash
   cp kiwi-admin/backend/src/main/resources/application-local.example.yml \
      kiwi-admin/backend/src/main/resources/application-local.yml
   ```

   填写 MySQL/MongoDB 密码、`kiwi.mongodb.init.admin-password`、`app.password.secret` 等。详见示例文件内注释。

2. **编译**（在仓库根目录，会按依赖顺序编译 `kiwi-common`、`kiwi-bpmn-*` 等）：

   ```bash
   mvn -pl kiwi-admin/backend -am compile -DskipTests
   ```

3. **启动**：在 IDE 中运行 `com.kiwi.framework.springboot.Application`，并设置 Spring Profile，例如：

   ```text
   --spring.profiles.active=local,dev
   ```

   - `local`：加载 `application-local.yml` 中的连接与密钥
   - `dev`：H2 作为 Camunda 数据源、MyBatis StdOut 日志等

4. 默认监听 **http://localhost:8000**。Camunda REST：**/engine-rest**；Swagger UI 由 springdoc 提供（路径以启动日志为准）。

首次启动且 `kiwi.mongodb.migration.enabled=true`（默认）时会初始化管理员与参考数据，须配置 `kiwi.mongodb.init.admin-password`。MongoDB 迁移说明见 [kiwi-admin/backend/README.md](kiwi-admin/backend/README.md)。

### 2. 前端

```bash
cd kiwi-admin/frontend
npm install
npm start
```

开发服务器默认 **http://localhost:4201**。

将 `src/environments/environment.ts` 中的 `api.baseUrl` 指向后端 API 根地址（本地一般为 `http://localhost:8000`；若经反向代理挂载在 `/kiwi-be` 等路径，需与部署一致）。`camundaEngineRestPath` 默认 `/engine-rest`。

### 3. CORS

后端 `application.yml` 中 `app.cors.allowed-origins` 默认包含 `http://localhost:4201`。自定义前端 Origin 时通过环境变量 `APP_CORS_ALLOWED_ORIGINS` 配置（逗号分隔）。

## 常用构建命令

```bash
# 编译后端及依赖模块
mvn -pl kiwi-admin/backend -am compile -DskipTests

# 打包（thin app jar + lib jar，供 deploy 使用）
mvn -pl kiwi-admin/backend -am package -DskipTests

# 前端生产构建
cd kiwi-admin/frontend && npm run build
```

SNAPSHOT 开发期若需 Maven 更频繁检查远程更新，可参考 [docs/maven/settings-dev-snippet.xml](docs/maven/settings-dev-snippet.xml)。

## 模块说明

### kiwi-bpmn-core

Camunda 引擎层扩展：`@ComponentDescription` / `@ComponentParameter` 组件元数据、输入输出变量映射、JUEL 容错、选择性 Job 重试、默认 asyncBefore 等。

### kiwi-bpmn-component

内置流程组件（通过 Spring `@Component` 注册，供 BPMN Service Task 引用）：

| 组件 | 说明 |
|------|------|
| Shell | 执行命令行 |
| HTTP 请求 | 发起 HTTP 调用 |
| 文件读/写 | 读写本地文件 |
| 变量赋值 | 流程变量赋值 |
| MongoDB | Mongo 文档操作 |
| Slurm | External Task：提交/跟踪 Slurm 作业（可选集成） |

Slurm 相关运维说明见 [kiwi-bpmn/kiwi-bpmn-component/docs/slurm-workdir-cleanup.md](kiwi-bpmn/kiwi-bpmn-component/docs/slurm-workdir-cleanup.md)。

### kiwi-admin

- **backend**：REST API、Camunda 嵌入式引擎、系统管理、BPM 项目管理、AI/MCP、通知与监控等
- **frontend**：基于 [ng-antd-admin](https://github.com/huajian123/ng-antd-admin) 演进的管理 UI，详见 [kiwi-admin/frontend/README.md](kiwi-admin/frontend/README.md)

## 主要配置项

| 变量 / 配置 | 说明 |
|-------------|------|
| `SPRING_DATA_MONGODB_URI` | 主 MongoDB 连接 |
| `SPRING_DATASOURCE_URL` | Camunda/MyBatis 关系库（非 dev profile） |
| `KIWI_MONGODB_MIGRATION_ENABLED` | 是否执行 Mongo 迁移，默认 `true` |
| `KIWI_INIT_ADMIN_PASSWORD` / `kiwi.mongodb.init.admin-password` | 首次管理员密码 |
| `APP_CORS_ALLOWED_ORIGINS` | 允许的前端 Origin |
| `KIWI_AI_API_KEY` / `DASHSCOPE_API_KEY` | AI 对话（通义）API Key |
| `KIWI_SA_TOKEN_STORAGE` | Sa-Token 存储：`mongodb`（默认）或 `redis` |

完整默认值见 `kiwi-admin/backend/src/main/resources/application.yml` 及 `application-local.example.yml`。

## 部署

- 后端远程部署：[kiwi-admin/backend/deploy/README.md](kiwi-admin/backend/deploy/README.md)（`deploy.py` + OpenSSH）
- 前端部署说明：[kiwi-admin/frontend/deploy/README.md](kiwi-admin/frontend/deploy/README.md)
- 远端进程管理：`kiwi-admin/backend/bin/restart.sh`（thin jar + lib jar，`-cp` 启动）

## 开发规范

- 非琐碎功能变更建议使用 **OpenSpec**（`openspec/`、`openspec list`）；Cursor 斜杠命令见 `.cursor/commands/opsx-*.md`
- Java 业务类优先实例方法，流程组件 `@ComponentParameter#key` 使用扁平下划线命名（禁止 `.`）
- 前端异步优先 RxJS `Observable`；列表接口注意统一响应中的 `CollectionResult.content`

## 相关文档

| 文档 | 内容 |
|------|------|
| [kiwi-admin/frontend/README.md](kiwi-admin/frontend/README.md) | 前端技术栈、脚本、AI 联调 |
| [kiwi-admin/backend/README.md](kiwi-admin/backend/README.md) | MongoDB 迁移约定 |
| [kiwi-admin/backend/deploy/README.md](kiwi-admin/backend/deploy/README.md) | 后端远程部署 |

## License

[MIT License](LICENSE)
