## ADDED Requirements

### Requirement: Spring Boot 4 主版本基线

Kiwi 仓库中基于 Spring Boot 的 Java 应用（含嵌入 Operaton 的管理后端）SHALL 以 **Spring Boot 4.0** 主版本线构建与运行。根 POM 及子模块 MUST 声明一致的 **4.0.x** 版本，且 MUST NOT 在面向生产的同一交付物中混用 Spring Boot 3.x 父 BOM。

#### Scenario: 版本属性一致

- **WHEN** 维护者检查根 `pom.xml`、`kiwi-admin/backend` 等声明 Spring Boot 版本的文件
- **THEN** 所声明版本 MUST 属于 **4.0.x** 且彼此一致

### Requirement: Operaton 2.x 与 Spring Boot 4 绑定

BPM 引擎集成 MUST 使用 **Operaton 2.x** 的 Spring Boot starter（`org.operaton.bpm.springboot` 组，版本由 Operaton 2.x BOM 管理），MUST NOT 在 Spring Boot 4 交付物中使用 Operaton 1.x（仅支持 Boot 3.5）或 Camunda `-4` starter。

#### Scenario: Boot 4 与 Operaton 2.x 共存

- **WHEN** 审查 `kiwi-admin/backend` 的 `spring-boot-starter-parent` 版本与 Operaton starter 依赖
- **THEN** Spring Boot MUST 为 **4.0.x**，Operaton BOM MUST 为 **2.x**，且二者 MUST 可同时通过 `mvn compile` 解析

### Requirement: Spring Framework 7 与 JDK 25

升级后运行时类路径上的 Spring Framework MUST 由 Spring Boot 4 管理的 **7.x** 组成。应用 MUST 继续满足仓库 **JDK 25** 构建约束（根 POM `requireJavaVersion`）。

#### Scenario: Framework 主版本与 Boot 4 一致

- **WHEN** 对打包产物检查 `spring-core` 模块版本
- **THEN** 其版本 MUST 与所选 Spring Boot 4 发行版配套的 **Framework 7.x** 一致，除非变更记录中有书面记载的受控覆盖
