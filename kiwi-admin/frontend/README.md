# Kiwi Admin 前端

**Kiwi** 管理端 **Angular** 应用：低代码后台、**BPMN 流程设计**、图表与表单等能力。本目录为独立 npm 工程，与 `kiwi-admin/backend` 通过 HTTP API 协作。

更完整的平台说明、仓库结构与后端启动方式见仓库根 [README.md](../../README.md)。

`src/app` 下各顶层目录的职责与新增代码放置规则见 **[docs/app-structure.md](docs/app-structure.md)**。

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Angular 21、TypeScript 5.9 |
| UI | ng-zorro-antd、Less（多主题：`default` / `dark` / `aliyun` / `compact`） |
| 流程 | BPMN.js、Camunda 相关（`camundaEngineRestPath` 与后端一致） |
| 图表 / 图编辑 | ECharts、@antv/g2plot、@antv/x6 |
| 其他 | CodeMirror、TinyMCE、JWT（`@auth0/angular-jwt`）、ngx-formly 等 |

前端由 [ng-antd-admin](https://github.com/huajian123/ng-antd-admin) 模板演进而来。

## 环境要求

- **Node.js**：建议 **Current/LTS** 中与 Angular 21 兼容的版本（参见 [Angular 版本要求](https://angular.dev/reference/versions)）。
- **包管理**：使用本仓库已锁定的依赖时，在 `kiwi-admin/frontend` 下执行 `npm install`。

## 快速开始

```bash
cd kiwi-admin/frontend
npm install
npm start
```

- 开发服务器默认：**http://localhost:4201**（`package.json` 中 `ng serve --o --port 4201`）。
- 请先启动后端（默认 API 见下文），并保证 CORS 中已包含该 Origin（根 `README` 说明 `http://localhost:4201`）。

## 配置说明

### API 基地址（`src/environments/`）

| 文件 | 用途 |
|------|------|
| `environment.ts` | **开发**：默认 `http://localhost:8088`（可在文件中改 `ip` / `port` 与后端 `server.port` 一致） |
| `environment.prod.ts` | **生产构建**：`ng build` 时通过 `angular.json` 的 `fileReplacements` 替换；请将 `baseUrl` 改为实际部署的后端地址 |

`api.camundaEngineRestPath` 默认 `/engine-rest`，与 Spring Boot + Camunda REST 约定一致；若后端改了 context，请同步修改。

### 开发代理（`proxy.conf.json`）

`/site/api` 会代理到 `http://localhost:3001/`（见 `pathRewrite`）。若本地没有该服务，可忽略相关请求或按需改代理目标。

`ng serve` 已关联该代理（`angular.json` → `serve.options.proxyConfig`）。

## 常用脚本

| 命令 | 说明 |
|------|------|
| `npm start` | 开发模式，打开浏览器，端口 **4201** |
| `npm run build` | 生产构建，`base-href=/kiwi-admin/` |
| `npm run watch` | development 配置下监听构建 |
| `npm test` | Karma 单元测试 |
| `npm run lint` / `npm run lint:fix` | ESLint |
| `npm run lint:style` | Stylelint（Less） |
| `npm run prettier` | Prettier 格式化 TS / HTML / Less / `public` 下 JSON |
| `npm run ng-high-memory-start` | 大内存启动 dev server（避免 OOM） |

## 构建与部署

```bash
npm run build
```

产物在 `dist/`。生产环境会使用 `environment.prod.ts`；部署到子路径时需与 `build` 的 `--base-href=/kiwi-admin/` 一致，或按实际路径修改 `package.json` 中的 `build` 脚本。

## 界面预览

![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110358.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110413.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110437.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110447.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110457.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110505.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110512.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110545.png)
![](screenshot/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-04-20%20110602.png)

## 目录结构（简要）

| 路径 | 说明 |
|------|------|
| `src/app/` | 业务页面、布局、核心服务（HTTP、路由等） |
| `src/environments/` | 环境变量与 API 基地址 |
| `src/styles/` | 全局与主题 Less |
| `public/` | 静态资源；MSW 等配置见 `package.json` 的 `msw` 字段 |

## 相关文档

- 根目录：**[README](../../README.md)**（后端启动、Maven、CORS、数据库）
- 远程部署脚本：**[kiwi-admin/script/README.md](../script/README.md)**
