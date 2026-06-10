# kiwi-bpmn-component-slack

独立可选模块：`SlackNotifyActivity`（`@Component("slackNotify")`）。

## 接入 backend

```xml
<dependency>
    <groupId>com.kiwi</groupId>
    <artifactId>kiwi-bpmn-component-slack</artifactId>
</dependency>
```

或 `mvn package` 后放入 `plugins/` 目录。

## 依赖

仅 `kiwi-bpmn-core` + JDK `HttpClient`，无额外 SDK。
