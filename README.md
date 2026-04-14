# Kiwi

**Kiwi 是一款面向企业的低代码开发平台**：在统一技术栈上提供可配置的管理能力、**可视化 BPMN 流程设计与运行**，以及可扩展的流程组件与外部任务集成，减少重复编码、缩短业务上线周期。

实现上，本仓库为**前后端一体**的多模块 Maven 工程：后端基于 **Spring Boot** 与 **Camunda BPM**，前端为 **Angular** 管理界面（`kiwi-admin/frontend`，由 [ng-antd-admin](https://github.com/huajian123/ng-antd-admin) 模板演进而来）。

## 平台定位（低代码）

| 能力 | 说明 |
|------|------|
| 流程低代码 | 基于 **BPMN.js** 的流程建模、与 **Camunda** 部署/运行对接，支持流程实例查看与变量洞察。 |
| 业务与管理端 | 管理端页面与权限、数据服务可组合扩展，面向「配置 + 少量代码」交付典型后台场景。 |
| 可扩展 | **kiwi-bpmn** 等模块承载流程扩展、外部任务等，便于按需接入业务系统。 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.4.x、Spring Data MongoDB、MyBatis、Sa-Token |
| 流程 | Camunda Platform 7.24（Spring Boot Starter、REST、Web 控制台、External Task Client） |
| 数据 | MongoDB、MySQL（Camunda/业务数据源）、Redis |
| 前端 | Angular 21、ng-zorro-antd、BPMN.js（流程设计相关页面） |

## 仓库结构

```
kiwi/
├── pom.xml                 # 父 POM（聚合模块与统一版本）
├── kiwi-common/            # 公共 Java 模块（实体与 Mongo 等共用能力）
├── kiwi-admin/
│   ├── backend/            # Spring Boot 主应用（端口见配置）
│   └── frontend/           # Angular 应用
└── kiwi-bpmn/              # BPMN 相关模块
    ├── kiwi-bpmn-core/
    ├── kiwi-bpmn-component/
    └── kiwi-bpmn-external-task/
```

## IntelliJ IDEA 与 Maven 多模块

- **导入方式**：在 IntelliJ IDEA 中**优先以仓库根目录**（例如 `d:\Projects\kiwi`）作为项目根导入，按 **Maven 多模块** 识别，便于与 Reactor 构建顺序、模块依赖保持一致。

- **单独编 backend**：`kiwi-admin/backend` 的 POM 已包含指向根父 POM 的 `<relativePath>`；在此前提下，若只关心 backend，仍建议：
  - 在仓库根对父 POM 先执行一次 **`mvn install`**，或
  - 始终在根目录使用 **`mvn -pl kiwi-admin/backend -am`**（`-am` 会顺带按顺序构建 **kiwi-common**、**kiwi-bpmn-*** 等 backend 所依赖的模块）。

- **本地仓库缓存**：若此前因父 POM 解析失败在 `~/.m2/repository` 中留下不完整产物，在 **relativePath 已正确** 时一般**不必**清空整个本地仓库；若仍异常，可对根 **`pom.xml`** 执行 **`mvn -U`** 强制更新元数据，或删除本地目录 **`~/.m2/repository/com/kiwi/kiwi-parent`**（对应 `com.kiwi:kiwi-parent:1.0.0`）后重试。

## 环境要求

- **JDK 17**
- **Maven 3.8+**
- **Node.js**（建议 LTS，与 Angular 21 兼容的版本）
- 运行期依赖：**MongoDB**、**MySQL**、**Redis**（具体库名与用途以 `application.yml` 为准）

## 配置说明

1. **后端**  
   - 主配置：`kiwi-admin/backend/src/main/resources/application.yml`（默认不含真实密钥，敏感项使用环境变量占位，如 `SPRING_DATASOURCE_PASSWORD`、`APP_PASSWORD_SECRET`、`CAMUNDA_ADMIN_PASSWORD` 等）。  
   - 本地覆盖：复制 `application-local.example.yml` 为 `application-local.yml`，填写数据库与 Redis 等；**`application-local.yml` 已加入 `.gitignore`，勿提交。** 启动时建议：`--spring.profiles.active=local,dev`（`dev` 可选，用于开启 MyBatis SQL 输出到控制台，仅调试用）。  
   - **CORS**：由 `app.cors.allowed-origins` 配置（逗号分隔）。本地默认包含 `http://localhost:4201` 等；**生产环境**请通过环境变量 `APP_CORS_ALLOWED_ORIGINS` 设置为实际前端 Origin，勿使用 `*`。

2. **前端 API**  
   - `kiwi-admin/frontend/src/environments/environment.ts` 中的 `api.baseUrl` 需指向后端（默认与后端 `server.port` 一致，例如 `http://localhost:8088`）。  
   - `proxy.conf.json` 将 `/site/api` 代理到 `http://localhost:3001/`，若你未单独起该服务，请按需调整或忽略相关路径。

3. **Camunda**  
   - 管理员账号等在 `application.yml` 的 `camunda.bpm.admin-user` 中配置；流程引擎 REST 与 Webapp 随 Spring Boot 应用一同启动。

## 本地运行

### 1. 启动后端

在仓库根目录：

```bash
mvn -pl kiwi-admin/backend -am spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,dev"
```

（若未使用 `application-local.yml`，可去掉 profile 参数，并确保环境变量或默认占位符与本地数据库一致。）

或在模块目录：

```bash
cd kiwi-admin/backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local,dev"
```

确保 MySQL、MongoDB、Redis 已按配置可用，否则启动会失败。

### 2. 启动前端

```bash
cd kiwi-admin/frontend
npm install
npm start
```

开发服务器默认 **http://localhost:4201**（见 `package.json` 中 `ng serve` 参数）。

### 3. 构建

- 后端：在仓库根执行 `mvn -pl kiwi-admin/backend -am clean package`（与上文「Maven 多模块」一致，`-am` 会构建依赖模块）。  
- 前端：`cd kiwi-admin/frontend && npm run build`（生产环境会替换为 `environment.prod.ts`）

## 其他说明

- **kiwi-bpmn** 子模块为流程扩展与外部任务等能力，随父工程一并构建。  

## 合作开发

欢迎对 **Kiwi 低代码平台** 感兴趣的同学**一起参与开发与维护**。你可以：

- 通过 **Issue** 讨论需求与方案、认领任务或反馈缺陷（请尽量写清背景与复现方式）；  
- 提交 **Pull Request**（较大改动建议先开 Issue 对齐方向，避免与现有路线冲突）；  
- 通过下方 **开发者联系** 中的邮箱沟通技术路线、模块分工或长期协作意向。

我们重视可读的提交说明与可复现的问题描述；代码与目录结构请尽量贴近现有模块习惯。期待你的参与。

## 开发者联系

| 方式 | 说明 |
|------|------|
| Issue | 在代码托管仓库提交 Issue，建议附上复现步骤、环境与关键日志。 |
| 邮件 | 418315052@qq.com |


## 许可证

MIT — 详见仓库根目录 [LICENSE](LICENSE)。
