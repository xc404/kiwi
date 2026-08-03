## 1. 宿主 SPI 与 OpenSpec 基线

- [x] 1.1 在 `kiwi-bpmn-core` 新增 `com.kiwi.bpmn.core.spi.JdbcConnectionSupplier`，从 `kiwi-bpmn-component` 迁移并更新 `JdbcActivity`、`KiwiJdbcConnectionSupplier` 引用
- [x] 1.2 移除或弃用 `kiwi-bpmn-component` 内原 `JdbcConnectionSupplier` 路径；去掉 `JdbcActivity`/`MongoActivity` 上阻碍插件加载的 `@ConditionalOnBean`（或等价处理）

## 2. 插件 JAR 打包（Maven）

- [x] 2.1 为 `kiwi-bpmn-component-kafka` 配置 `provided` 依赖 + `maven-shade-plugin`，产出 `*-plugin.jar`；本地验证 PluginLoader 可加载 `plugin_kafkaPublish`
- [x] 2.2 为 `kiwi-bpmn-component-rabbitmq`、`kiwi-bpmn-component-s3`、`kiwi-bpmn-component-slack` 重复 2.1 模式
- [x] 2.3 为 `kiwi-bpmn-component` 配置 shade 打包（commons-io、jsch、mail 等第三方 lib 打入 jar；core/spring/operaton provided）
- [x] 2.4 根 `pom.xml` 增加 profile `build-plugins`：package 时将各 `*-plugin.jar` COPY 至 `kiwi-admin/backend/plugins/` 与 `docker/plugins/`；`.gitignore` 忽略 `backend/plugins/*.jar`

## 3. backend 依赖裁剪与 Slurm 传递依赖

- [x] 3.1 从 `kiwi-admin/backend/pom.xml` 移除 `kiwi-bpmn-component`、`kiwi-bpmn-component-example`、`kiwi-bpmn-component-kafka/rabbitmq/s3/slack` 依赖（保留 `kiwi-bpmn-component-slurm`）
- [x] 3.2 审查 `kiwi-bpmn-component-slurm/pom.xml`：移除对 `kiwi-bpmn-component` 的传递依赖（`mvn dependency:tree` 验证无重复 core 组件 classpath Bean）
- [x] 3.3 修复 backend 编译引用（`OpenApiComponentGenerator` Javadoc 等）使其不依赖 `kiwi-bpmn-component` 编译 classpath

## 4. PluginLoader 增强

- [x] 4.1 `loadJar` 完成后对同 JAR 已注册 plugin bean 执行 `autowireBean`，验证 `webhookOutbound` → `httpRequest`
- [x] 4.2 新增只读 `buildPluginJarIndex(): Map<String,String>`（componentId → jar 文件名），不触发 reload

## 5. 数据迁移与运行时回退

- [x] 5.1 Mongock `@ChangeUnit`：`bpmProcess.bpmnXml` 与 `bpmComponent` 中 `classpath_{key}` → `plugin_{key}`（排除 Slurm 相关 key）
- [x] 5.2 更新 `V20250616_002__BpmProcess.json` 种子；`BpmComponentService.resolveHttpRequestParentComponentId()` fallback 改为 `plugin_httpRequest`；CLI 生成器 shell 回退改为 `plugin_shell`
- [x] 5.3 （可选）`BpmComponentService.getComponent` 对 `classpath_*` 未命中 fallback 查 `plugin_*`

## 6. dev / Docker / 文档

- [x] 6.1 `docker/backend/Dockerfile` build 阶段加 `-Pbuild-plugins`；运行时 `COPY docker/plugins/` → `/app/plugins/`
- [x] 6.2 更新 `docs/bpm-component.zh-CN.md`：官方组件改插件分发；dev 需 `mvn package -Pbuild-plugins`；更新 `AGENTS.md` 开发流程一句

## 7. 验证

- [x] 7.1 `mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests` 成功；启动后 `GET /bpm/component/list` 含 `plugin_httpRequest`、`classpath_*` 仅 Slurm（若启用）
- [x] 7.2 种子/迁移后流程 HTTP 节点可执行；JDBC 在 `KiwiJdbcConnectionSupplier` 存在时可执行
- [x] 7.3 OpenAPI / CLI 生成器 `parentId` 指向 `plugin_httpRequest` / `plugin_shell`；插件 upload/reload API 仍正常
