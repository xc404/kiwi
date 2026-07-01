# bpm-component-plugins Specification

## Purpose

BPM 组件插件 JAR 的加载、分发与元数据契约：包括官方/第三方插件目录、`plugin_*` 组件注册，以及包级 `component-bundle.json` 清单与富描述 API。

## Requirements

### Requirement: 插件 JAR 内嵌 component-bundle.json 清单

系统 SHALL 支持从插件 JAR 路径 `META-INF/kiwi/component-bundle.json` 读取包级清单（`schemaVersion`、`name`、`version`、`components[]` 等）。无清单时 SHALL 回退为以 JAR 文件名为包名、以 `@ComponentDescription` 注解扫描填充组件列表。

#### Scenario: 有有效清单时返回包元数据

- **WHEN** 插件 JAR 含合法 `component-bundle.json` 且各 `components[].key` 均存在于注解扫描结果
- **THEN** `BpmComponentBundleReader.describeJar` SHALL 返回 `bundle.name`、`bundle.version` 及按 JSON 顺序排列的组件列表

#### Scenario: 清单 key 与 JAR 不一致时拒绝

- **WHEN** `component-bundle.json` 中某 `key` 在 JAR 注解扫描中不存在
- **THEN** preview 或 describe SHALL 返回 HTTP 400 及明确错误信息

#### Scenario: 扫描到未列出组件时产生 warnings

- **WHEN** JAR 含注解组件但 JSON 未列出该 `key`
- **THEN** descriptor `warnings` SHALL 包含提示且该组件 SHALL 以 `source=scanned` 追加到列表，不阻断安装

### Requirement: 插件 API 返回富描述 DTO

`GET /bpm/component/plugins` SHALL 返回 `BpmComponentPluginDescriptor[]`（含 `fileName`、`bundle`、`components`、`warnings`、`fileSizeBytes`、`sha256`），**不再**返回裸 `List<String>` 文件名。

#### Scenario: 列表含包名与组件数

- **WHEN** 客户端调用 `GET /bpm/component/plugins`
- **THEN** 响应 `content[]` 每项 SHALL 含 `bundle.name` 与 `components` 数组

### Requirement: 上传前 preview 校验

系统 SHALL 提供 `POST /bpm/component/plugins/preview`（multipart `file`），**不落盘**，返回与安装后相同结构的 descriptor；校验失败 SHALL 返回 400。

#### Scenario: preview 通过后 upload 落盘

- **WHEN** 客户端先 preview 再通过 `POST /bpm/component/plugins/upload` 上传同一 JAR
- **THEN** JAR SHALL 写入 `plugins/` 且 reload 后 `GET /plugins` SHALL 包含该 descriptor

### Requirement: 官方与 example 模块维护 bundle JSON

`kiwi-bpmn-component`、`kiwi-bpmn-component-kafka|rabbitmq|s3|slack|example` SHALL 在 `src/main/resources/META-INF/kiwi/component-bundle.json` 维护与注解一致的清单；`mvn -Pbuild-plugins package` 后 JSON SHALL 打入对应 plugin JAR。

#### Scenario: example JAR 含清单

- **WHEN** 构建 `kiwi-bpmn-component-example` plugin JAR
- **THEN** JAR 内 SHALL 存在 `META-INF/kiwi/component-bundle.json` 且 `demoGreeting` 条目与 `DemoGreetingActivity` 一致
