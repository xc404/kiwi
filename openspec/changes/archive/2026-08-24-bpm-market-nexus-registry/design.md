## Context

- 站内模板市场（`bpm-workflow-template-market`）已实现 MongoDB 存储与 zip 安装。
- Nexus 3 提供 Raw / Maven hosted 仓库，适合存放大文件与版本目录。
- 第一版不建独立 Registry 服务，索引为静态 `market/index.json`。

## Goals / Non-Goals

**Goals**

- 可配置一个或多个远程市场源（Nexus base URL + index 路径）。
- 统一索引列出模板包与插件；Kiwi 拉取、缓存、展示、安装。
- SHA-256 与 `kiwiMinVersion` 校验；模板缺失组件时明确提示。
- 发布脚本上传制品并更新索引。

**Non-Goals**

- 审核流、GPG 签名、开发者门户、服务端搜索。
- 替换站内 MongoDB 模板市场。

## Decisions

### 1. 索引：静态 JSON

`market/index.json` 由发布脚本维护，Kiwi 定期或手动 sync 拉取。后续可演进为 Registry API。

### 2. 仓库布局

| 路径 | 内容 |
|------|------|
| `market/index.json` | 统一索引 |
| `templates/{slug}/{version}/{slug}-{version}.kiwi-template-pack` | 模板包 |
| `templates/{slug}/{version}/manifest.json` | 侧车 manifest |
| `plugins/{groupPath}/{artifactId}/{version}/{artifactId}-{version}.jar` | 插件 JAR |
| `plugins/{groupPath}/{artifactId}/{version}/manifest.json` | 插件 manifest |

`downloadUrl` / `manifestUrl` 在 index 中为绝对 URL 或相对 base-url 的路径。

### 3. 配置

```yaml
kiwi:
  bpm:
    remote-market:
      enabled: false
      kiwi-version: 1.0.0-SNAPSHOT
      cache-ttl-seconds: 300
      sources:
        - id: default
          name: ...
          base-url: http://host:8081/repository/kiwi-market-raw/
          index-path: market/index.json
          username: ...
          password: ...
```

### 4. API 前缀

`GET/POST /bpm/remote-market/*`，operationId 前缀 `bpmRemoteMarket_`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `` | 列表（query: type, keyword, sourceId） |
| GET | `/{slug}/versions/{version}` | 详情（含 manifest 拉取） |
| POST | `/sync` | 刷新缓存 |
| POST | `/templates/{slug}/versions/{version}/install` | 下载并安装模板 |
| POST | `/plugins/{slug}/versions/{version}/install` | 下载并安装插件 |

`slug` 在 index 中唯一标识条目（模板 slug 或插件 artifactId）。

### 5. 安装复用

- **模板**：`BpmRemoteMarketDownloadService` 下载 → 校验 sha256 → `BpmTemplatePackBundleService.importAndInstallFromBytes`。
- **插件**：下载 JAR → 校验 sha256 → `BpmComponentBundleService.installJarFromBytes` → `reloadAndDeploy`。

### 6. 兼容性

- `kiwiMinVersion`：语义化版本比较（主.次.补丁，忽略 `-SNAPSHOT` 后缀比较）。
- `requiredComponentKeys`：对比 MongoDB 中已部署组件 `key`；缺失则 HTTP 409 + 缺失列表。

### 7. HTTP 客户端

使用 `java.net.http.HttpClient`（与 `OpenApiSpecFetcher` 一致），支持 Basic 认证。

### 8. 前端

新菜单「远程市场」`/bpm/remote-market`；Tab 筛选 template/plugin；详情页展示 manifest 与安装按钮。

## Risks / Trade-offs

- 静态 index 并发发布可能冲突 → 第一版由脚本串行更新；后续 Registry 解决。
- Nexus 匿名读与 HTTPS 由部署方配置，不在本 change 范围。

## Migration Plan

- 默认 `remote-market.enabled=false`；启用需配置 `sources`。
- 无数据迁移。

## Open Questions

- 多源合并去重策略：第一版按 source 顺序合并，同 slug+version 后者覆盖（记录 sourceId）。
