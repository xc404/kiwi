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

## 待人工验证

- [ ] 应用启动与 `/engine-rest` 冒烟
- [ ] H2 / MySQL 引擎库 schema 升级
- [ ] BPM 部署、实例、External Task、Spin JSON
- [ ] `spring.data.mongodb` 在 Boot 4 下是否需改为 `spring.mongodb`
- [ ] spring-ai-alibaba、MCP 在 Boot 4 运行时行为
- [x] 推送 `camunda` 分支与 tag 至远程（`6e833f8`，远程已存在 `refs/heads/camunda` 与 annotated tag `camunda`）

## 编译验证

```bash
mvn -pl kiwi-admin/backend -am compile -DskipTests
```

会话末次：**BUILD SUCCESS**。
