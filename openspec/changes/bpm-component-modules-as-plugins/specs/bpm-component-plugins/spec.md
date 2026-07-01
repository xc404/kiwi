## ADDED Requirements

### Requirement: 官方组件以插件 JAR 分发

除 Slurm 模块外，系统 SHALL 将下列 Maven 模块的业务组件实现打包为可独立加载的插件 JAR（fat jar，命名约定 `{artifactId}-{version}-plugin.jar`），且 SHALL NOT 再通过 `kiwi-admin/backend` 的 Maven compile 依赖将其打入 backend fat jar：

- `kiwi-bpmn-component`
- `kiwi-bpmn-component-kafka`
- `kiwi-bpmn-component-rabbitmq`
- `kiwi-bpmn-component-s3`
- `kiwi-bpmn-component-slack`

`kiwi-bpmn-component-slurm` SHALL 继续通过 backend Maven 依赖以 classpath 方式加载（`classpath_*` 元数据）。

#### Scenario: backend 不再直接依赖核心组件模块

- **WHEN** 检查 `kiwi-admin/backend/pom.xml` 依赖列表
- **THEN** SHALL NOT 包含 `kiwi-bpmn-component`、`kiwi-bpmn-component-kafka`、`kiwi-bpmn-component-rabbitmq`、`kiwi-bpmn-component-s3`、`kiwi-bpmn-component-slack` 的 compile 依赖
- **AND** SHALL 仍包含 `kiwi-bpmn-component-slurm` 的 compile 依赖

#### Scenario: 插件 JAR 加载后元数据为 plugin_ 前缀

- **WHEN** `plugins/` 目录存在官方 `kiwi-bpmn-component` 插件 JAR 且 `bpm.component.plugins-enabled` 为 true
- **THEN** 系统 SHALL 将其中 `httpRequest` 等组件注册为 Spring Bean
- **AND** MongoDB / 组件缓存中对应组件 `id` SHALL 为 `plugin_httpRequest`（`source=plugin`，`key=httpRequest`）

---

### Requirement: build-plugins Maven profile 产出种子插件

根 POM SHALL 提供 Maven profile `build-plugins`；激活该 profile 执行 `package` 时，系统 SHALL 构建上述插件 JAR 并 SHALL 将其 COPY 至至少下列目录之一供运行时使用：

- `kiwi-admin/backend/plugins/`
- `docker/plugins/`

#### Scenario: 本地 dev 构建后可启动官方组件

- **WHEN** 开发者执行 `mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests`
- **THEN** `kiwi-admin/backend/plugins/`（或配置项 `bpm.component.plugins-dir` 指向的目录）SHALL 包含 `kiwi-bpmn-component` 插件 JAR
- **AND** 启动 backend 后 `GET /bpm/component/list` 响应 SHALL 包含 `plugin_httpRequest`

---

### Requirement: 宿主 SPI 接口位于 kiwi-bpmn-core

需要由 backend 宿主实现的组件契约接口（至少 `JdbcConnectionSupplier`）SHALL 定义在 `kiwi-bpmn-core` 的 `com.kiwi.bpmn.core.spi`（或等价包）中。插件 JAR SHALL NOT 将 `kiwi-bpmn-core` 打入 fat jar（Maven `provided` scope）。

#### Scenario: JDBC 组件可从插件注入宿主连接供应器

- **WHEN** backend 注册 `KiwiJdbcConnectionSupplier` Bean 且 `plugins/` 含含 `jdbcActivity` 的插件 JAR
- **THEN** 插件加载后 `jdbcActivity` Bean SHALL 成功创建且 SHALL 能调用宿主 `JdbcConnectionSupplier.openConnection`

---

### Requirement: PluginLoader 支持同 JAR 内 Bean 依赖

`BpmComponentPluginLoader` 在加载单个插件 JAR 时，若该 JAR 内存在多个需相互注入的组件 Bean（例如 `webhookOutbound` 依赖 `httpRequest`），系统 SHALL 在注册全部相关 Bean 后完成它们之间的依赖注入，使运行时委托可正常执行。

#### Scenario: Webhook 组件可调用 HTTP 组件

- **WHEN** 官方 `kiwi-bpmn-component` 插件 JAR 已加载
- **THEN** 执行绑定 `plugin_webhookOutbound` 的流程节点 SHALL 成功触发 HTTP 请求逻辑（不因 `httpRequest` 未注入而失败）

---

### Requirement: PluginLoader 提供只读 JAR 索引

`BpmComponentPluginLoader` SHALL 提供只读方法（不触发 reload 或 Bean 注册），根据当前 `plugins/` 目录扫描各 JAR 内带 `@ComponentDescription` 的 delegate 类，返回 `componentId`（`plugin_{key}`）到 JAR 文件名的映射，供后续模板包导出等场景使用。

#### Scenario: 索引包含已安装插件的 componentId

- **WHEN** `plugins/` 中存在提供 `httpRequest` 的 JAR 且插件已可被正常加载
- **THEN** `buildPluginJarIndex()`（或等价 API）SHALL 包含键 `plugin_httpRequest` 且值 SHALL 为对应 JAR 文件名

---

### Requirement: classpath 组件 id 迁移为 plugin

对于已插件化的官方组件（非 Slurm），系统 SHALL 将持久化数据中 `classpath_{key}` 形式的组件 id 与 BPMN 内 `kiwi:componentId` / `camunda:property componentId` 迁移为 `plugin_{key}`（key 不变）。迁移 SHALL NOT 修改 Slurm 相关 `classpath_*` id。

#### Scenario: 种子流程 HTTP 节点使用 plugin 前缀

- **WHEN** 新环境执行 Mongo 种子迁移后打开种子流程 BPMN
- **THEN** HTTP 服务任务的 `componentId` SHALL 为 `plugin_httpRequest` 而非 `classpath_httpRequest`

#### Scenario: Slurm 组件 id 保持不变

- **WHEN** 迁移执行完成且环境中存在 Slurm classpath 组件
- **THEN** Slurm 相关组件 `id` SHALL 仍以 `classpath_` 为前缀

---

### Requirement: 父组件 id 解析回退使用 plugin 前缀

`BpmComponentService` 在缓存中未找到 `httpRequest` 或 `shell` 父组件时，用于 OpenAPI / CLI 生成器等场景的 id 回退值 SHALL 分别为 `plugin_httpRequest` 与 `plugin_shell`（替代原 `classpath_*` 回退）。

#### Scenario: httpRequest 未在缓存时回退 plugin id

- **WHEN** 调用 `resolveHttpRequestParentComponentId()` 且缓存中无 `key=httpRequest` 的组件
- **THEN** 返回值 SHALL 为 `plugin_httpRequest`
