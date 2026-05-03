## ADDED Requirements

### Requirement: Java 后端 Spring Boot 主版本基线

Kiwi 仓库中基于 Spring Boot 的 Java 应用（含聚合 BPM 能力的管理后端及相关模块）SHALL 以 **Spring Boot 4.0** 主版本线构建与运行；构建描述符（Maven 父 POM 或等价属性）MUST 声明与该主版本线一致的 Spring Boot 版本，且同一仓库内 MUST NOT 长期混用 Spring Boot 3.x 与 4.x 父 BOM（遗留分支或实验分支除外，且 MUST 在变更说明中标注）。

#### Scenario: 版本属性一致

- **WHEN** 维护者检查根 `pom.xml`、`kiwi-admin/backend`、`cyroems` 等声明 Spring Boot 父版本或 `${spring-boot.version}` 的位置
- **THEN** 所声明的 Spring Boot 版本 MUST 属于 **4.0.x** 且彼此一致（或与团队文档声明的单一补丁策略一致）

### Requirement: Camunda 与 Spring Boot 4 对齐方式

当应用包含 Camunda 7 Spring Boot 集成时，依赖坐标 MUST 使用 Camunda 官方为 Spring Boot 4 提供的 **`-4` 后缀** starter 系列；Camunda 7 版本 MUST 不低于 Camunda 文档中针对 Spring Boot 4 所要求的最低补丁（当前文档约定为 **7.24.3** 及以上与 Boot 4 组合）。MUST NOT 在面向生产的同一交付物中混用非 `-4` 的 Camunda starter 与 Spring Boot 4 父 BOM。

#### Scenario: Starter 命名符合 Boot 4

- **WHEN** 审查 `camunda-bpm-spring-boot-starter` 相关依赖
- **THEN** 工件 artifactId MUST 为带 **`4`** 后缀的变体（例如 `camunda-bpm-spring-boot-starter-4`），且版本满足上述最低 Camunda 补丁要求

### Requirement: JDK 构建与运行基线

上述应用在升级后的交付物 SHALL 继续满足本仓库对 **JDK 25** 的构建链约束（与根 POM `requireJavaVersion` 一致）；在移除针对 Spring Boot 3 的 Spring Framework 6.2.x BOM 覆盖后，运行时类路径上的 Spring Framework 版本 MUST 由 Spring Boot 4 管理的 **7.x** 组成，除非在变更记录中明确记载并经评审的例外覆盖。

#### Scenario: Framework 主版本与 Boot 4 一致

- **WHEN** 对打包产物执行依赖树或等价检查（例如 `spring-core` 模块版本）
- **THEN** Spring Framework 核心模块版本 MUST 与所选 Spring Boot 4 发行版配套 **7.x** 一致，或符合书面记载的受控覆盖说明
