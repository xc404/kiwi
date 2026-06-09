# kiwi-bpmn-component-example

第三方 BPM **组件**开发示例模块，演示 `@ComponentDescription` + `JavaDelegate` 最小实现。

## 包含内容

- `DemoGreetingActivity`（`@Component("demoGreeting")`）：读取 `name`，写入 `greeting = "Hello, " + name`

## 接入 kiwi-admin backend

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

## 复制为自有组件

1. 复制本模块或仅复制 `DemoGreetingActivity` 类
2. 修改 `@Component("yourBeanKey")` 与 `@ComponentDescription`
3. 实现 `JavaDelegate#execute`
4. 作为 Maven 依赖编入 backend，或后续以 plugin JAR 分发

约定见仓库 `docs/bpm-component.zh-CN.md`。
