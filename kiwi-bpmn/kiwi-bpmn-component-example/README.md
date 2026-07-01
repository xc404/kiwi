# kiwi-bpmn-component-example

第三方 BPM **组件**开发示例模块，演示 `@ComponentDescription` + `JavaDelegate` 最小实现。

## 包含内容

- `DemoGreetingActivity`（`@Component("demoGreeting")`）：读取 `name`，写入 `greeting = "Hello, " + name`

## 打成 plugin JAR 并上传

继承 `kiwi-bpmn-component-parent` 后，在仓库根目录：

```bash
mvn -pl kiwi-bpmn/kiwi-bpmn-component-example package -Dkiwi.build.plugins=true -DskipTests
```

产物：`kiwi-bpmn/kiwi-bpmn-component-example/target/kiwi-bpmn-component-example-*-plugin.jar`

- 复制到 `kiwi-admin/backend/plugins/` 后重启 backend，或
- 管理端 **工作流 → 组件插件** → 上传 JAR

组件元数据 id 为 `plugin_demoGreeting`（`plugin_` + `@Component` bean key）。

打包契约（`provided`、shade excludes）见 `docs/bpm-component.zh-CN.md`「插件 JAR 打包契约」。

## 接入 kiwi-admin backend（classpath 方式，可选）

在 `kiwi-admin/backend/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.kiwi</groupId>
    <artifactId>kiwi-bpmn-component-example</artifactId>
</dependency>
```

重启后端后：

1. Mongo 出现组件元数据 `classpath_demoGreeting`
2. 设计器「业务组件」面板「示例」分组可见「Demo 问候」

## 本地验证

```bash
mvn -pl kiwi-bpmn/kiwi-bpmn-component-example test
mvn -pl kiwi-admin/backend -am compile -DskipTests
```

## 最小 Maven 依赖（第三方组件）

```xml
<dependency>
    <groupId>com.kiwi</groupId>
    <artifactId>kiwi-bpmn-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <scope>provided</scope>
</dependency>
```

`ExecutionUtils` 已位于 `kiwi-bpmn-core`（`com.kiwi.bpmn.core.utils`），**无需**依赖 `kiwi-bpmn-component`。

## 复制为自有组件

1. 复制本模块或仅复制 `DemoGreetingActivity` 类
2. 修改 `@Component("yourBeanKey")` 与 `@ComponentDescription`
3. 实现 `JavaDelegate#execute`
4. 作为 Maven 依赖编入 backend（classpath），或打成 plugin JAR 分发（推荐第三方热更新）

约定见仓库 `docs/bpm-component.zh-CN.md`（含「插件 JAR 打包契约」）。
