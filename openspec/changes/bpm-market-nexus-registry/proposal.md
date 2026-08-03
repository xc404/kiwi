## Why

Kiwi 站内模板市场（C1/C2）已支持 MongoDB 发布与 `.kiwi-template-pack` 文件包，但缺少公网侧制品分发能力。以 Nexus 作为远程仓库、静态 `market/index.json` 作为索引，可在不新建 Registry 服务的前提下，让各 Kiwi 实例浏览、下载并安装远程模板包与插件 JAR。

## What Changes

- 新增 `kiwi.bpm.remote-market` 配置：远程市场源 URL、认证、缓存 TTL、本实例 Kiwi 版本。
- 定义 Nexus 目录布局与 `market/index.json` schema（模板 + 插件统一索引）。
- 新增 `bpmRemoteMarket_*` REST API：列表、详情、同步索引、远程安装模板/插件。
- 下载时校验 SHA-256；安装前校验 `kiwiMinVersion` 与模板 `requiredComponentKeys`。
- 复用现有 `BpmTemplatePackBundleService.importAndInstall` 与 `BpmComponentBundleService.uploadJar` 路径。
- 前端新增「远程市场」菜单与页面（列表、详情、筛选、安装）。
- 提供 Nexus 部署脚本与 `scripts/market/publish` 发布工具。
- **不实现**：审核流、GPG 签名、独立 Registry 服务、开发者门户（留后续 change）。

## Capabilities

### New Capabilities

- `bpm-remote-market-source`：远程市场源配置、索引拉取与内存缓存、手动 sync。
- `bpm-remote-market-index`：统一 `market/index.json` 格式与 Nexus 目录约定。
- `bpm-remote-market-install`：远程下载、校验、模板包与插件 JAR 安装语义。

### Modified Capabilities

- （无。不修改 `openspec/specs/` 下既有 capability 的 normative 行为。）

## Impact

- **后端**：`com.kiwi.project.bpm.config`（`BpmRemoteMarketProperties`）、`dto`、`service`（RemoteMarket/Download/Install）、`BpmRemoteMarketCtl`；扩展 Bundle 服务支持字节流安装。
- **前端**：`/bpm/remote-market`、`/bpm/remote-market/:slug/:version`；`R__SysMenu.json` 菜单。
- **运维**：`docker/nexus/`、`scripts/nexus/`、`scripts/market/`；`docs/bpm-remote-market/`。
- **MCP**：`bpmRemoteMarket_*` operationId 自动注册。
- **依赖**：无新 Maven 依赖；HTTP 使用 `java.net.http.HttpClient`。
