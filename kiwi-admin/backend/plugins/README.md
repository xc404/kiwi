# BPM 官方组件插件 JAR

本目录存放官方 BPM 组件的 **plugin JAR**，由 `BpmComponentPluginLoader` 在启动时加载（配置项 `bpm.component.plugins-dir`，默认 `plugins`，相对**工作目录**）。

JAR 经 `maven-shade-plugin` 打包，**仅含本模块与第三方 compile 依赖**；Spring、Operaton、`kiwi-bpmn-core` 等由 backend 宿主提供（`provided` + shade `artifactSet` excludes）。详见 [`docs/bpm-component.zh-CN.md`](../../../docs/bpm-component.zh-CN.md)「插件 JAR 打包契约」。

## 日常开发

clone 仓库后，在 IDE 中从 **`kiwi-admin/backend`** 运行 `Application`（profile `local,dev`）即可，**无需**先执行 `mvn -Pbuild-plugins`。

IDE 工作目录须为 `kiwi-admin/backend`（IntelliJ：`$MODULE_WORKING_DIR$` 或 `$ProjectFileDir$/kiwi-admin/backend`；VS Code：Run 时 cwd 设为 `kiwi-admin/backend`）。

## 修改官方组件后

若改动了 `kiwi-bpmn/kiwi-bpmn-component*` 模块，在仓库根目录重新打包并提交 JAR：

```bash
mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests
git add kiwi-admin/backend/plugins/*.jar
git commit -m "chore: refresh BPM component plugin JARs"
```

## 当前 JAR

| 文件 | 模块 |
|------|------|
| `kiwi-bpmn-component-*-plugin.jar` | Shell、HTTP、JDBC、Mongo 等核心组件 |
| `kiwi-bpmn-component-kafka-*-plugin.jar` | Kafka 发布 |
| `kiwi-bpmn-component-rabbitmq-*-plugin.jar` | RabbitMQ 发布 |
| `kiwi-bpmn-component-s3-*-plugin.jar` | S3 对象操作 |
| `kiwi-bpmn-component-slack-*-plugin.jar` | Slack 通知 |
| `kiwi-bpmn-component-payment-*-plugin.jar` | 支付宝 / 微信沙箱下单与查单 |

Docker/CI 构建仍使用 `docker/plugins/`（构建时生成，不入库）。
