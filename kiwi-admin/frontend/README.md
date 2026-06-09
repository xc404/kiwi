# Kiwi Admin 前端

Angular 管理端：低代码后台、**BPMN 流程设计**、图表与表单。独立 npm 工程，通过 HTTP 调用 [backend](../backend/README.md)。平台介绍、演示地址与界面截图见仓库根 [README.zh-CN.md](../../README.zh-CN.md)（[English](../../README.md)）。

## 技术栈

| 类别 | 说明 |
|------|------|
| 框架 | Angular 21、TypeScript 5.9 |
| UI | ng-zorro-antd、Less（`default` / `dark` / `aliyun` / `compact`） |
| 流程 | BPMN.js、实例查看走 `/bpm/process-instance` API |
| 图表 / 图编辑 | ECharts、@antv/g2plot、@antv/x6 |
| 其他 | CodeMirror、TinyMCE、JWT、ngx-formly 等 |

基于 [ng-antd-admin](https://github.com/huajian123/ng-antd-admin) 演进。

## 代码结构

| 路径 | 说明 |
|------|------|
| `src/app/core/` | 全局服务、拦截器、守卫等 |
| `src/app/layout/` | 布局壳、侧栏、顶栏 |
| `src/app/pages/` | 业务页面（含 `bpm` 流程设计、系统管理等） |
| `src/app/shared/` | 可复用组件与管道 |
| `src/app/config/` | 应用级配置 |
| `src/app/utils/` | 工具函数 |
| `src/environments/` | `environment.ts` / `environment.prod.ts` |
| `src/styles/` | 全局与主题 Less |
| `public/` | 静态资源 |

## 环境要求

- **Node.js**：与 [Angular 21](https://angular.dev/reference/versions) 兼容的 Current/LTS。
- 在 `kiwi-admin/frontend` 下执行 `npm install`。

## 快速开始

```bash
cd kiwi-admin/frontend
npm install
npm start
```

- 开发服务器：**http://localhost:4201**（`ng serve --port 4201`）。
- 先启动后端（`local,dev` 下一般为 **http://localhost:8000**），并确保 CORS 包含 `http://localhost:4201`。

## 配置说明

### API 基地址（`src/environments/`）

| 文件 | 用途 |
|------|------|
| `environment.ts` | 开发：`api.baseUrl` 默认 `http://localhost:8000/`（与 `environment.ts` 内 `port` 及后端 `server.port` 一致） |
| `environment.prod.ts` | 生产构建替换；改为实际后端地址 |

`api.baseUrl` 为 API **根地址**，不要以 `/ai` 结尾。BPM 实例查看走 `/bpm/process-instance/*` 封装 API，不再直连 `/engine-rest`。

### 开发代理（`proxy.conf.json`）

`/site/api` 代理到 `http://localhost:3001/`。无该服务时可忽略或修改目标。`ng serve` 已通过 `angular.json` 启用 `proxyConfig`。

## 常用脚本

| 命令 | 说明 |
|------|------|
| `npm start` | 开发模式，端口 **4201** |
| `npm run build` | 生产构建，`base-href=/kiwi-admin/` |
| `npm run watch` | development 监听构建 |
| `npm test` | Karma 单元测试 |
| `npm run lint` / `npm run lint:fix` | ESLint |
| `npm run lint:style` | Stylelint（Less） |
| `npm run prettier` | 格式化 TS / HTML / Less / `public` 下 JSON |
| `npm run ng-high-memory-start` | 大内存 dev server |

## 构建与部署

```bash
npm run build
```

产物在 `dist/`。子路径部署须与 `--base-href=/kiwi-admin/` 一致。远程上传见 [deploy/README.md](deploy/README.md)。

## AI 辅助（Kiwi · AI）

需后端配置 `KIWI_AI_API_KEY` / `DEEPSEEK_API_KEY`（见 [backend README](../backend/README.md) 与 `application.example.yml`）。

| 入口 | 说明 |
|------|------|
| 右下角浮窗 | 「Kiwi · AI」对话卡片 |
| 仪表盘「AI 对话」 | 路由 `.../dashboard/ai-chat`（菜单需在系统管理中配置） |

联调：`AiChatService.assistant()` → **`POST /ai/assistant`**；补全 → **`POST /ai/chat`**。响应 `actions` 可含路由跳转、BPM 设计器字段（`toolbar`、`bpmnXml`、`appendComponent` 等）。

## 相关文档

- [../../README.zh-CN.md](../../README.zh-CN.md) — 仓库总览、目录 Map、截图（[English](../../README.md)）  
- [../backend/README.md](../backend/README.md) — 后端配置与启动  
- [deploy/README.md](deploy/README.md) — 远程部署  
