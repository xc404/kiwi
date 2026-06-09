## 0. 保留 Camunda 基线（必须最先执行）

- [x] 0.1 确认 `master` 当前 HEAD **尚未包含** Operaton/Boot 4 迁移改动；记录该提交的完整 SHA（`6e833f8`）
- [x] 0.2 在 `master` HEAD 创建 annotated tag：`git tag -a camunda -m "Camunda 7.24 + Spring Boot 3.5 baseline"`
- [x] 0.3 自同一提交创建分支：`git branch camunda`（或 `git checkout -b camunda` 后切回 `operaton-migration`）
- [x] 0.4 推送远程：`git push origin refs/heads/camunda` 与 `git push origin refs/tags/camunda`；在 README 或变更说明中记录 tag/分支用途
- [x] 0.5 验证：`git checkout camunda` 后 `pom.xml` 仍为 `camunda.version=7.24.0` 与 Boot 3.5.x

## 1. 调研与 POC（Operaton 2.x + Boot 4）

- [x] 1.1 确认 **Operaton 2.0.x / 2.1.x** 与 **Spring Boot 4.0.x** 在 Maven Central 可解析；记录 `operaton-bom`、starter、Spin、external-task、REST 的精确 artifactId
- [x] 1.2 在 `operaton-migration` 分支做最小 POC：仅升 Boot 4 父 POM + Operaton 2.x 引擎依赖，列出编译阻塞项（Jackson 3、Jakarta EE 11、spring-ai-alibaba 等）
- [ ] 1.3 POC 启动：访问 `/engine-rest`、部署样例 BPMN、确认引擎版本为 Operaton 2.x

## 2. Spring Boot 4 升级

- [x] 2.1 根 `pom.xml`：`spring-boot-starter-parent` → **4.0.x**；移除 Boot 3 专用 `spring-framework-bom` 6.2.x 覆盖（验证后按需加 7.x 受控覆盖）
- [x] 2.2 对齐第三方 BOM：**spring-ai-alibaba**、**sa-token**、**springdoc-openapi**、**mica**、**mybatis-plus** 等 Boot 4 兼容版本（编译通过；运行时待验证）
- [ ] 2.3 按 [Boot 4 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) 处理配置属性重命名与废弃 API（Mongo `spring.data.mongodb` 等待确认）
- [x] 2.4 `mvn dependency:tree` 确认 `spring-core` 为 7.x，无 Boot 3 传递依赖锁死

## 3. Operaton 2.x Maven 依赖

- [x] 3.1 用 `operaton.version` + `operaton-bom` import 替换 `camunda.version`；删除所有 `org.camunda.bpm` 运行时依赖
- [x] 3.2 `kiwi-admin/backend/pom.xml`：Operaton 2.x starter、webapp、rest、Spin
- [x] 3.3 `kiwi-bpmn/kiwi-bpmn-external-task/pom.xml`：Operaton engine 与 external-task-client
- [x] 3.4 处理 Jackson 3 / Spin 兼容性（若需 `spring-boot-jackson2` 或 Operaton 文档推荐模块，写入 README）

## 4. 源码迁移（OpenRewrite + 人工）

- [ ] 4.1 配置 `rewrite-maven-plugin` + `migrate-camunda-recipe`，执行 `MigrateFromCamunda`（已用手动 `org.camunda` → `org.operaton` 替代）
- [x] 4.2 全仓库清除 `import org.camunda`；修复反射、字符串、`META-INF/services`
- [x] 4.3 人工审阅：`kiwi-bpmn-core` 插件与 Job 重试、`NullableJuelProcessEnginePlugin`、External Task、BPM REST DTO
- [x] 4.4 修复 Boot 4 / Framework 7 编译错误（测试代码一并更新）

## 5. 配置与环境

- [x] 5.1 全部 `application*.yml`：`camunda.bpm` → `operaton.bpm`；Boot 4 相关属性同步
- [x] 5.2 环境变量：`CAMUNDA_*` → `OPERATON_*`；更新 Docker Compose、`bin/config`、deploy 文档
- [x] 5.3 确认 `kiwi.bpm.*` 自定义配置仍生效

## 6. 前端与 BPMN

- [x] 6.1 更新 `environment*.ts` 与 `frontend/README.md` 注释（Operaton 2.x + Boot 4；`camundaEngineRestPath` 仍指向 `/engine-rest`）
- [x] 6.2 **不修改** `camunda-element-model.ts` / `camunda-bpmn-moddle`
- [x] 6.3 清查 Script Task / GraalVM JS 兼容性（仓库 BPMN 无 `scriptTask`）

## 7. 文档与变更收敛

- [x] 7.1 更新根 `README.md` / `README.zh-CN.md`：技术栈为 Operaton 2.x + Spring Boot 4；说明 `camunda` tag/分支
- [x] 7.2 更新 `kiwi-admin/backend/README.md`：迁移、回滚（checkout `camunda`）、引擎库备份
- [x] 7.3 更新 `docs/bpm-component.zh-CN.md` 引擎表述
- [x] 7.4 在 `openspec/changes/upgrade-spring-boot-4` 注明已被本变更 supersede（归档前处理）

## 8. 验证与发布

- [x] 8.1 `mvn -pl kiwi-admin/backend -am compile`（及约定测试）通过
- [ ] 8.2 冒烟：BPM CRUD、部署、启动、实例/历史、ManualTask、async Job、Spin JSON 变量
- [ ] 8.3 Slurm External Task 端到端（环境允许时）
- [ ] 8.4 H2 与 MySQL 克隆库验证 Operaton 2.x schema 升级
- [ ] 8.5 AI/MCP、鉴权、系统 REST 抽样回归
- [ ] 8.6 合并 `operaton-migration` → `master`；发布 BREAKING 说明与 `camunda` 回滚指引
