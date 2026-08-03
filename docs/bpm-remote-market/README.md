# Kiwi 远程市场 `market/index.json` Schema

`schemaVersion` 当前为 **1**。完整 JSON Schema 见同目录 `index.schema.json`。

## 目录布局（Nexus Raw 仓库 `kiwi-market-raw`）

```text
market/index.json
templates/{slug}/{version}/{slug}-{version}.kiwi-template-pack
templates/{slug}/{version}/manifest.json
plugins/{groupIdPath}/{artifactId}/{version}/{artifactId}-{version}.jar
plugins/{groupIdPath}/{artifactId}/{version}/manifest.json
```

`groupIdPath` = Maven `groupId` 将 `.` 替换为 `/`，例如 `com.kiwi.bpmn` → `com/kiwi/bpmn`。

## index.json 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| schemaVersion | int | 是 | 当前为 1 |
| generatedAt | string | 是 | ISO-8601 时间 |
| items | array | 是 | 市场条目 |

### items[] 公共字段

| 字段 | 类型 | 必填 |
|------|------|------|
| type | `template` \| `plugin` | 是 |
| slug | string | 是 |
| name | string | 是 |
| version | string | 是 |
| summary | string | 否 |
| category | string | 否 |
| tags | string[] | 否 |
| kiwiMinVersion | string | 否 |
| downloadUrl | string | 是 |
| sha256 | string | 是（小写 hex） |
| manifestUrl | string | 否 |
| signatureUrl | string | 否 |

### 模板专属

| 字段 | 类型 |
|------|------|
| kind | string |
| processCount | int |
| requiredComponentKeys | string[] |

### 插件专属

| 字段 | 类型 |
|------|------|
| componentKeys | string[] |
| mavenCoordinate | `{ groupId, artifactId, version }` |

## 示例

见 `fixtures/sample-index.json`。
