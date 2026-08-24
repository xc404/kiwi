## 1. Nexus 与文档

- [x] 1.1 `docker/nexus/docker-compose.yml` 与 README
- [x] 1.2 `scripts/nexus/setup-repos` 与 `verify-upload`
- [x] 1.3 `docs/bpm-remote-market/` 目录与 index schema

## 2. 后端配置与模型

- [x] 2.1 `BpmRemoteMarketProperties` + `application.yml`
- [x] 2.2 DTO：`BpmRemoteMarketItem`、`BpmRemoteMarketIndex` 等
- [x] 2.3 `BpmRemoteMarketHttpFetcher`（HTTP + Basic 认证）

## 3. 后端服务

- [x] 3.1 `BpmRemoteMarketService`（索引拉取、缓存、列表、详情）
- [x] 3.2 `BpmRemoteMarketDownloadService`（下载 + SHA-256）
- [x] 3.3 `BpmRemoteMarketInstallService`（版本/组件校验、安装）
- [x] 3.4 扩展 `BpmTemplatePackBundleService` / `BpmComponentBundleService` 支持字节流

## 4. REST API

- [x] 4.1 `BpmRemoteMarketCtl` + OpenAPI `bpmRemoteMarket_*`

## 5. 前端

- [x] 5.1 `remote-market.service.ts`、列表页、详情页
- [x] 5.2 `bpm-routing.ts` 与 `R__SysMenu.json` 菜单

## 6. 发布与验收

- [x] 6.1 `scripts/market/publish` 脚本
- [x] 6.2 单元测试 + E2E 验证脚本与测试 fixtures
