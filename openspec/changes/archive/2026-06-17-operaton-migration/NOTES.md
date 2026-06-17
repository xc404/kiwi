# Operaton 迁移实施笔记

## 基线

- **Camunda 末态提交**：`6e833f8`（`master` 在迁移前 HEAD）
- **标签/分支**：`camunda`（annotated tag + 分支，供回滚）
- **实施分支**：`operaton-migration`

## 版本

| 组件 | 迁移前 | 迁移后 |
|------|--------|--------|
| Spring Boot | 3.5.8 | 4.0.0 |
| BPM 引擎 | Camunda 7.24.0 | Operaton 2.1.0 |
| Sa-Token | spring-boot3-starter | spring-boot4-starter 1.45.0 |
| springdoc | 2.x | 3.0.0 |

## Maven Central

Operaton 2.1.x 在阿里云等镜像可能未同步。根 `pom.xml` 已添加仓库 `operaton-maven-central`（`https://repo1.maven.org/maven2`）。构建失败时可：

```bash
mvn -U -pl kiwi-admin/backend -am compile -DskipTests
```

并清理 `~/.m2/repository/org/operaton` 中损坏缓存。

## 保留的 Camunda 语义

- BPMN XML：`camunda:` 扩展命名空间与 moddle（前端 `camunda-bpmn-moddle` 不改）
- REST 路径：`/engine-rest`
- `BpmnParser.CAMUNDA_BPMN_EXTENSIONS_NS` 等 Operaton 兼容常量（有 deprecation 警告，可后续替换）

## 配置迁移

- `camunda.bpm.*` → `operaton.bpm.*`
- `CAMUNDA_*` → `OPERATON_*`（环境变量）

## 验证范围（§8.2–8.6 不做）

迁移代码与编译已通过（§8.1）。**不再**单独开展 OpenSpec 规定的正式冒烟、schema 克隆验证、Slurm E2E、AI/MCP 抽样回归或「合并 operaton-migration 分支」发布流程；日常以本地 dev / 线上 Demo 使用为准。曾规划的 `OperatonEngineSmokeTest`、`SMOKE.md`、`operaton-smoke.ps1` 已移除。

## 历史备注（可选后续观察，非本 change 交付项）

- `spring.data.mongodb` 在 Boot 4 下是否需改为 `spring.mongodb`
- spring-ai-alibaba、MCP 在 Boot 4 运行时行为

## 编译验证

```bash
mvn -pl kiwi-admin/backend -am compile -DskipTests
```

会话末次：**BUILD SUCCESS**。
