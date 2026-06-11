## Why

Camunda 7 Community Edition 已于 2025 年 10 月停止维护并归档；Kiwi 需要可持续的开源 BPM 引擎路线。**Operaton 2.x** 在延续 Camunda 7 嵌入式模型的同时，原生支持 **Spring Boot 4** 与 **Spring Framework 7**，可与 Kiwi 技术栈升级一并完成，避免「先 Operaton 1.x + Boot 3.5、再二次升 Boot 4」的双次破坏性迁移。

迁移开始前，须将当前 **`master` 上 Camunda 7.24 + Boot 3.5.8 的基线**固化为 **`camunda` 标签与 `camunda` 分支**，供回滚、对照与仅安全补丁场景使用；主开发线随后在 `operaton-migration`（或合并后的 `master`）上推进 Operaton 2.x + Boot 4。

## What Changes

- **版本控制（迁移前置）**：在 `master` 当前提交创建 **annotated tag `camunda`**，并自同提交拉出长期维护分支 **`camunda`**（指向 Camunda 7.24 末态）；迁移工作不在 `camunda` 分支上叠加 Operaton 改动。
- **Spring Boot**：根 BOM / `spring-boot-starter-parent` 及子模块提升至 **Spring Boot 4.0.x**；移除为 Boot 3 引入的 **Spring Framework 6.2.x BOM 覆盖**，以 Boot 4 管理的 **Framework 7.x** 为准。
- **BPM 引擎**：Maven 依赖从 `org.camunda.*` 替换为 **`org.operaton.*`（Operaton 2.x BOM）**；**不再**采用 Camunda 官方 `-4` starter 或 Operaton 1.x（1.x 仅支持 Boot 3.5，与目标不符）。
- **BREAKING（配置）**：`camunda.bpm.*` → **`operaton.bpm.*`**；环境变量 `CAMUNDA_*` → `OPERATON_*`（见 design 过渡期策略）。
- **OpenRewrite**：使用 [migrate-from-camunda-recipe](https://github.com/operaton/migrate-from-camunda-recipe) 迁移 `org.camunda` → `org.operaton`；Boot 4 废弃 API、Jackson 3、Jakarta EE 11 等按 Spring Boot 4 迁移指南人工收尾。
- **第三方对齐**：**spring-ai-alibaba**、**sa-token**、**springdoc-openapi**、**mica**、**mybatis-plus** 等须验证 Boot 4 兼容版本（吸收原 `upgrade-spring-boot-4` 范围，但 BPM 侧以 Operaton 2.x 替代 Camunda `-4`）。
- **保留 BPMN `camunda:` 扩展命名空间**与 **`/engine-rest`**；前端 `camunda-bpmn-moddle` / `camunda-element-model` 不要求改 namespace。
- **文档**：README、部署说明更新为 Operaton 2.x + Boot 4；注明 `camunda` 标签/分支用途与 GraalVM JavaScript（Script Task）注意事项。
- **废弃并行变更**：本变更完成后，`openspec/changes/upgrade-spring-boot-4` 的目标由本变更覆盖，实施时不再单独推进该 change。

## Capabilities

### New Capabilities

- `operaton-engine-runtime`：Operaton 2.x 嵌入式引擎运行时基线（依赖、配置前缀、REST、BPMN 兼容、与 Camunda 7.24 行为边界）。
- `java-backend-runtime`：Kiwi Java 后端在 **Spring Boot 4.0.x + JDK 25** 上的构建与运行基线（含 Operaton 2.x 集成，非 Camunda）。

### Modified Capabilities

- （无）`openspec/specs/slurm-workdir-cleanup` 等行为规格不变；Slurm External Task 契约不变。

## Impact

- **Git**：`camunda` tag + `camunda` branch；`operaton-migration` 分支承载实施；合并后 `master` 为 Operaton 线。
- **构建**：根 `pom.xml`、`kiwi-admin/backend`、`kiwi-bpmn-*`、第三方 BOM；约 50+ 处 `org.camunda` Java 引用。
- **配置**：全部 `application*.yml`、Docker、deploy、`bin/config`。
- **前端**：注释与文档；BPMN 设计器 moddle 可不改。
- **运维**：引擎库 schema 自 Camunda 7.24 升级至 Operaton 2.x 须备份；回滚应用代码可 checkout `camunda` 标签/分支，数据库须用迁移前备份恢复。
- **集成方**：`/engine-rest` 与 Kiwi BPM API 契约不变；cryoEMS 等对接方无需改 BPMN namespace。
