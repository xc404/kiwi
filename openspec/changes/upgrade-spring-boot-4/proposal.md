## Why

Kiwi 当前基于 Spring Boot 3.5.x 与 JDK 25；升级到 **Spring Boot 4.x** 可与上游长期支持节奏对齐，获得安全修复与 Spring Framework 7 / Boot 4 生态的一致行为，并为后续依赖升级腾出空间。迁移有一定破坏性（Camunda 工件坐标、BOM 策略），适合以规格化变更一次性收敛。

## What Changes

- 将根 BOM / `spring-boot-starter-parent` 及子模块中显式声明的 Spring Boot 版本提升至 **4.0.x**（与团队锁定的补丁线一致，具体版本以实现时 Central 与兼容性矩阵为准）。
- **BREAKING**：迁移至 Camunda 官方为 Spring Boot 4 提供的 **`-4` 后缀** starter（并通常需 **Camunda ≥ 7.24.3**）；Maven 坐标与类路径与当前 `camunda-bpm-spring-boot-starter` 系列不同。
- **BREAKING**：移除或替换当前为 JDK 25 / ASM 问题而覆盖的 **Spring Framework 6.2.x BOM** 策略，改为以 Spring Boot 4 自带的 Spring Framework **7.x** 为准；若有传递依赖再次压低 Framework 版本，仅在验证后以受控方式覆盖。
- 核对并升级/替换与 Boot 4 不兼容的第三方：**spring-ai-alibaba**、**sa-token**、**springdoc-openapi**、**mica**、**mybatis-plus** 等，版本以实现时各项目发行说明为准。
- `cyroems`（独立 parent）与 `kiwi-admin/backend` 等所有声明 Spring Boot 父版本或属性的模块 **版本对齐**。
- **BREAKING**：配置属性、自动配置包名或废弃 API 若随 Boot 4 迁移指南变更，则同步修改 `application*.yml` 与代码（以官方迁移文档清单为准）。

## Capabilities

### New Capabilities

- `java-backend-runtime`: 定义 Kiwi Java 后端（含 BPM/Camunda）在升级后对外可见或运维相关的**运行时基线**（Spring Boot 主版本线、JDK、Camunda starter 类型），便于与其它变更区分「行为规格」与「单纯依赖号」。

### Modified Capabilities

- （无）现有 `openspec/specs/` 中与 Slurm 清理相关的规范不涉及 Spring Boot；本次不要求修改该能力的需求文本。

## Impact

- **构建**：`pom.xml`（根、`kiwi-admin/backend`、`cyroems` 等）、Camunda 相关模块依赖；可能调整 `maven-enforcer-plugin` 若与 JDK/Boot 约束联动。
- **运行时**：`kiwi-admin` 后端、`cryoems-bpm`、`cyroems` 任务服务等基于 Spring Boot 的进程；需全量回归 BPM、REST、Mongo、鉴权、AI/MCP 等路径。
- **运维/文档**：若有 JVM 参数、镜像基础镜像或部署说明依赖 Boot 3 行为，需更新内部文档（非本次规格正文强制项，可在 tasks 中勾选）。
