# Kiwi

**Kiwi** 是基于 [Camunda BPM 7](https://camunda.com/platform/) 的工作流编排与管理平台：可视化 BPMN 设计、可扩展流程组件、系统管理后台。本仓库为 Maven 多模块 monorepo，管理端前后端分离部署。

## 在线演示

管理后台：[https://www.kiwi-admin.cn](https://www.kiwi-admin.cn)

## 特性

- **BPMN 流程设计**：Angular + BPMN.js，属性面板与后端组件元数据联动
- **Camunda 引擎**：流程定义/实例、`/engine-rest`、External Task、异步 Job 与可配置重试
- **可插拔流程组件**：Shell、HTTP、文件读写、变量赋值、MongoDB、Slurm 等
- **管理后台**：用户/角色/菜单/部门/字典、Sa-Token、Personal Access Token
- **低代码工具**：代码生成、JDBC 与表结构浏览
- **AI 辅助**：Spring AI（通义 DashScope）、内置 MCP，支持页面导航与 BPM 设计器编排
- **数据迁移**：Mongock + JSON 参考数据（类 Flyway 约定）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java **25**、Spring Boot **3.5**、Camunda **7.24**、MongoDB、MyBatis、Sa-Token |
| 前端 | Angular **21**、ng-zorro-antd、BPMN.js、ECharts、@antv/x6 |
| 构建 | Maven（多模块）、npm |
| 规格 | [OpenSpec](openspec/)（`spec-driven`） |

## 仓库结构

```
kiwi/
├── kiwi-common/                 # 公共实体、Mongo/MyBatis 工具
├── kiwi-bpmn/
│   ├── kiwi-bpmn-core/          # 组件注解、变量映射、Job 重试
│   ├── kiwi-bpmn-component/     # 内置 Delegate（Shell、HTTP、Slurm…）
│   └── kiwi-bpmn-external-task/
├── kiwi-admin/
│   ├── backend/                 # Spring Boot 主应用（见子 README）
│   └── frontend/                # Angular 管理端（见子 README）
├── docs/                        # 截图、Maven 片段等
├── openspec/                    # 变更规格与任务
└── LICENSE
```

### 模块与关键路径

| 路径 | 职责 |
|------|------|
| [kiwi-common/](kiwi-common/) | 跨模块实体与数据访问基类 |
| [kiwi-bpmn/kiwi-bpmn-core/](kiwi-bpmn/kiwi-bpmn-core/) | `@ComponentDescription` / `@ComponentParameter`、变量映射、JUEL 容错、Job 重试 |
| [kiwi-bpmn/kiwi-bpmn-component/](kiwi-bpmn/kiwi-bpmn-component/) | 内置流程组件（Shell、HTTP、MongoDB、Slurm 等）；Slurm 运维见 [slurm-workdir-cleanup.md](kiwi-bpmn/kiwi-bpmn-component/docs/slurm-workdir-cleanup.md) |
| [kiwi-bpmn/kiwi-bpmn-external-task/](kiwi-bpmn/kiwi-bpmn-external-task/) | External Task 抽象与重试 |
| [kiwi-admin/backend/](kiwi-admin/backend/) | `com.kiwi.framework`（启动、安全、Mongo 迁移、异常处理）+ `com.kiwi.project.{system,bpm,ai,tools,monitor,notification}` |
| [kiwi-admin/frontend/](kiwi-admin/frontend/) | `src/app/{core,layout,pages,shared,config,utils}`；BPMN 编辑器在 `pages/bpm` |
| [docs/screenshots/](docs/screenshots/) | 管理端界面截图 |
| [docs/maven/settings-dev-snippet.xml](docs/maven/settings-dev-snippet.xml) | SNAPSHOT 开发期 Maven settings 片段 |

## 界面预览

![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110358.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110413.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110437.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110447.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110512.png)
![](docs/screenshots/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110602.png)

更多截图见 [docs/screenshots/](docs/screenshots/)。

## 快速开始（摘要）

**依赖**：JDK 25、Maven 3.x、MongoDB；联调 Camunda 需 MySQL（`dev` profile 可用内嵌 H2）；前端需 Node.js（与 Angular 21 兼容）。

1. **后端**：复制 [application.example.yml](kiwi-admin/backend/src/main/resources/application.example.yml) → `application-local.yml`，填写连接与 `kiwi.mongodb.init.admin-password`；仓库根目录 `mvn -pl kiwi-admin/backend -am compile -DskipTests`；IDE 运行 `com.kiwi.framework.springboot.Application`，Profile 建议 `local,dev`（端口 **8000**）。详见 [kiwi-admin/backend/README.md](kiwi-admin/backend/README.md)。

2. **前端**：`cd kiwi-admin/frontend && npm install && npm start` → **http://localhost:4201**；`environment.ts` 中 `api.baseUrl` 与后端端口一致。详见 [kiwi-admin/frontend/README.md](kiwi-admin/frontend/README.md)。

## 相关文档

| 文档 | 内容 |
|------|------|
| [kiwi-admin/backend/README.md](kiwi-admin/backend/README.md) | 后端架构、配置、本地启动、Mongo 迁移 |
| [kiwi-admin/frontend/README.md](kiwi-admin/frontend/README.md) | 前端架构、环境、脚本、AI 联调 |
| [kiwi-admin/backend/deploy/README.md](kiwi-admin/backend/deploy/README.md) | 后端远程部署 |
| [kiwi-admin/frontend/deploy/README.md](kiwi-admin/frontend/deploy/README.md) | 前端远程部署 |

非琐碎功能变更建议使用 **OpenSpec**（`openspec list`）；Java/流程组件与前端异步约定见 `.cursor/rules/`。

## License

[MIT License](LICENSE)
