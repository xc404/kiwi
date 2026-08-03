## Why

Kiwi 内置 BPM 组件（HTTP、Shell、JDBC、Kafka 等）当前通过 `kiwi-admin/backend` 直接 Maven 依赖 `kiwi-bpmn-component*` 模块，以 Spring 扫描注册为 `classpath_*` 组件。这导致：(1) backend fat jar 体积大、可选集成（Kafka/S3 等）无法按需裁剪；(2) 与已有 `plugins/` 插件加载机制双轨并行，模板包导出/跨实例分发（任务 B）难以将组件 JAR 与流程一并打包。将官方组件改为插件 JAR 分发，是模板市场「一键带组件」与组件生态扩展的前置架构解耦。

## What Changes

- **宿主 SPI 抽取**：将 `JdbcConnectionSupplier` 等宿主契约接口迁至 `kiwi-bpmn-core`，避免插件 ClassLoader 双加载导致注入失败。
- **插件 JAR 打包**：对 `kiwi-bpmn-component`、`kiwi-bpmn-component-kafka/rabbitmq/s3/slack`（及可选 `example`）配置 `maven-shade-plugin` 产出 `*-plugin.jar`；根 Maven profile `build-plugins` 在 `package` 时 COPY 至 `kiwi-admin/backend/plugins/` 与 `docker/plugins/`。
- **backend 依赖裁剪**：从 `kiwi-admin/backend/pom.xml` 移除上述 component 模块依赖；**保留** `kiwi-bpmn-component-slurm`（含 `@Configuration` 多 Bean 生态，本期不插件化）。
- **PluginLoader 增强**：同 JAR 内 Bean 依赖注入（如 `webhookOutbound` → `httpRequest`）；预埋只读 `buildPluginJarIndex()`（供后续模板包捆绑 JAR 复用）。
- **BPMN / 元数据迁移**：存量 `classpath_{key}` → `plugin_{key}`（排除 Slurm 相关 key）；更新种子数据与 `resolveHttpRequestParentComponentId()` 等 fallback。
- **dev / Docker**：本地与 Docker 构建需 `-Pbuild-plugins`；Dockerfile COPY `plugins/` 至运行目录。
- **BREAKING**：全新部署或空 `plugins/` 目录将无法使用 HTTP/Shell 等官方组件；开发者需先执行 `mvn package -Pbuild-plugins`。

## Capabilities

### New Capabilities

- `bpm-component-plugins`：官方组件插件 JAR 打包与分发、`build-plugins` Maven profile、种子插件目录、PluginLoader 增强、backend 依赖裁剪（Slurm 除外）、`classpath_*` → `plugin_*` 迁移。

### Modified Capabilities

- `bpm-openapi-component-generator`：`httpRequest` 父组件 id 回退由 `classpath_httpRequest` 改为 `plugin_httpRequest`。
- `bpm-cli-help-component-generator`：`shell` 父组件 id 回退由 `classpath_shell` 改为 `plugin_shell`。

## Impact

- **Maven**：7 个 `kiwi-bpmn-component*` 模块 `pom.xml`；根 `pom.xml` profile；`kiwi-admin/backend/pom.xml`；`kiwi-bpmn-component-slurm/pom.xml`（传递依赖解耦）。
- **后端**：`kiwi-bpmn-core`（SPI）、`BpmComponentPluginLoader`、`BpmComponentService`、Mongock 迁移、`KiwiJdbcConnectionSupplier`。
- **Docker / CI**：`docker/backend/Dockerfile`、可选 CI smoke test。
- **数据**：Mongo `bpmProcess.bpmnXml`、`bpmComponent` 中 `classpath_*` id 迁移；种子 JSON `V20250616_002__BpmProcess.json`。
- **文档**：`docs/bpm-component.zh-CN.md`、`AGENTS.md` 开发流程。
- **前端**：无直接改动；设计器读取的组件 `id` 前缀由 `classpath_` 变为 `plugin_`（对 UI 透明）。

## 非目标

- Slurm 模块插件化（留待 PluginLoader 支持 `@Configuration` 子上下文或 Slurm 拆分）。
- 组件 semver 解析与独立组件市场 Registry。
- 模板包 zip 内捆绑 JAR（任务 B，change `bpm-workflow-template-market`）。
- 移除 `ClasspathBpmComponentProvider`（Slurm 仍依赖 classpath 扫描）。
