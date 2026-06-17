## ADDED Requirements

### Requirement: Classpath 资源在 JAR 部署下可加载

kiwi-admin 在以 Spring Boot 可执行 JAR（含嵌套 classpath）运行时，对打包在应用内的配置或模板资源（例如 `permission/permission.json`、默认 BPM 流程模板）的读取 SHALL 不依赖 `java.io.File` 或 `ResourceUtils.getFile` 等「必须存在文件系统路径」的 API；SHALL 使用 Spring `Resource` / `ResourceLoader` / `InputStream` 等可在 JAR 内工作的机制。

#### Scenario: 默认 BPM 模板在 fat JAR 内可读

- **WHEN** 应用以打包后的 `kiwi-admin` JAR 启动且 `xbpm.process-definition.template-path` 为默认的 `classpath:bpm/bpm-template.xml`（或未覆盖为等价的 classpath 资源）
- **THEN** `BpmProcessDefinitionService` 能成功将模板内容读入内存，且创建流程定义时可基于该模板生成 BPMN XML，不因「无法解析为绝对文件路径」而启动失败

#### Scenario: 权限清单在 fat JAR 内可读

- **WHEN** 应用以打包后的 JAR 启动且 `permission/permission.json` 位于 classpath
- **THEN** 权限清单加载成功，不因资源位于 `jar:nested:` 或嵌套 JAR 内而失败
