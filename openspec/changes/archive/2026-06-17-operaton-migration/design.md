## Context

- Kiwi 当前 **`master`**：**Camunda 7.24.0** + **Spring Boot 3.5.8** + **JDK 25**；BPM 扩展在 `kiwi-bpmn-*`，配置 `camunda.bpm.*`，REST `/engine-rest`。
- **Operaton 版本线**：**1.x** 仅支持 Boot 3.5；**2.0+** 面向 **Boot 4 + Framework 7**，并 **放弃 Boot 3**（[Operaton 2.0 发布说明](https://docs.operaton.org/docs/documentation/reference/release-notes/2_0/)）。
- 仓库已有未完成的 `upgrade-spring-boot-4` change（Camunda `-4` starter）；本变更 **合并其 Boot 4 范围**，BPM 侧改为 **Operaton 2.x**，避免两条破坏性升级线并行。
- 实施分支：`operaton-migration`（已从 `master` 拉出）。

## Goals / Non-Goals

**Goals:**

- 在迁移实施前，将 **`master` 当前提交**固化为 **`camunda` 标签 + `camunda` 分支**，永久保留 Camunda 7.24 末态。
- 主开发线一次性升级到 **Operaton 2.x + Spring Boot 4.0.x**，保持 BPMN 流程、External Task、REST 集成行为等价。
- 第三方依赖（AI、鉴权、OpenAPI 等）在 Boot 4 下可构建、可启动、核心路径冒烟通过。

**Non-Goals:**

- 在 `camunda` 分支上继续叠加 Operaton 功能开发（该分支仅维护/热修 Camunda 线，若需要）。
- 迁移至 Camunda 8 / Zeebe。
- 修改 BPMN `camunda:` namespace 或替换前端 moddle。
- 本变更内规定 Angular 大版本升级。

## Decisions

1. **Git 保留策略（用户明确要求）**
   - 在 **`master` 当前 HEAD**（迁移 PR 合并前、未含 Operaton 改动的提交）执行：
     - `git tag -a camunda -m "Last Camunda 7.24 + Spring Boot 3.5 baseline"`
     - `git branch camunda`（指向同一提交；若远程尚无则 `git push origin camunda` 与 `git push origin camunda` tag）
   - **`camunda` 分支**作为长期只读/维护线；**`operaton-migration`** 承载全部升级提交，合并后成为新 `master`。
   - 不在本变更中 force-push 或改写已有 `master` 历史；标签一旦推送视为不可变基线。

2. **目标版本线**
   - **Operaton 2.0.x 或 2.1.x**（实施时取 Central 最新稳定补丁，须声明支持 Boot 4.0.x）。
   - **Spring Boot 4.0.x** 稳定补丁（避免 4.1 RC，除非团队书面接受）。
   - **JDK 25** 保持根 POM enforcer 要求；Operaton 2.x 官方支持 17/21/25。

3. **依赖替换（Operaton 2.x，非 Camunda `-4`）**
   - 根 POM：`operaton-bom` import，`operaton.version` 替代 `camunda.version`；`spring-boot-starter-parent` → **4.0.x**。
   - 引擎工件（以 2.x BOM 为准）：`operaton-bpm-spring-boot-starter`、`webapp`、`rest`、Spin、`external-task-client` 等。
   - **删除**所有 `org.camunda.bpm` 运行时依赖；**不**引入 `camunda-bpm-spring-boot-starter-4`。

4. **Spring / Jakarta 栈**
   - 移除 Boot 3 时代的 `spring-framework-bom` **6.2.x** 强制 import；以 Boot 4 自带 Framework **7.x** 为主。
   - 关注 Operaton 2.0 的 **Jakarta EE 11**、**Jackson 3** 影响；Operaton 侧可能需 `spring-boot-jackson2` 兼容模块——以 POC 为准，在 tasks 记录最终选型。
   - Boot 4 迁移清单：[Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。

5. **代码迁移顺序**
   1. 打 `camunda` tag/branch（零代码改动）。
   2. Boot 4 父 POM + 第三方 BOM 升级（可能先编译失败）。
   3. OpenRewrite `MigrateFromCamunda` → `org.operaton`。
   4. 人工修复：`ProcessEnginePlugin`、Job 重试、External Task、REST DTO、Boot 4 废弃 API。
   5. `camunda.bpm` → `operaton.bpm` 配置。

6. **BPMN / 前端 / REST**：与 1.x 方案相同——**不改** `camunda:` namespace、`camunda-bpmn-moddle`；`/engine-rest` 默认保留。

7. **与 `upgrade-spring-boot-4` 的关系**：该 change 假设 Camunda `-4` starter；本变更 **取代** 其 BPM 部分与 Boot 4 目标，实施完成后将 `upgrade-spring-boot-4` 标记为被 supersede（归档时注明）。

8. **环境变量**：`OPERATON_*` 为主；首版 **不** 保留 `CAMUNDA_*` 别名（`camunda` 分支保留旧变量文档即可）。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Operaton 2.x + Jackson 3 / Spin 传递依赖冲突 | 早期 POC；对照 Operaton issue #1672；必要时显式引入 jackson2 兼容模块 |
| spring-ai-alibaba / MCP 未支持 Boot 4 | 查 BOM 发行说明；分模块验证；必要时暂缓 AI 子集 |
| 双重大迁移（引擎 + Boot）一次 PR 过大 | 分支内分 commit 阶段（tag → Boot 4 → Operaton）；tasks 分阶段勾选 |
| `camunda` tag 未在打 tag 前冻结 master | **tasks 1.x 必须为迁移第一步**；合并前核对 tag 指向提交不含 Operaton 改动 |
| 引擎库 schema 升级不可逆 | 备份；H2/MySQL 克隆库预演 |
| GraalVM JS Script Task | BPMN 清查；文档化 |

## Migration Plan

1. **`master` HEAD** → 创建 **`camunda` tag + `camunda` branch** 并推送远程。
2. 在 **`operaton-migration`** 分支：Boot 4 POC → Operaton 2.x 依赖 + OpenRewrite → 配置与文档。
3. 全量回归：BPM、REST、Mongo、鉴权、AI/MCP、Slurm（若可用）。
4. PR 合并至 `master`；发布说明：**BREAKING**（Boot 4、Operaton、配置前缀）；指向 `camunda` 标签作回滚参考。

**Rollback**：

- **代码**：`git checkout camunda` 或部署 `camunda` 标签构建产物。
- **数据库**：使用迁移前备份；勿假设 Operaton 2.x 库可降回 Camunda 7 直接使用。

## Open Questions

- 锁定的 **Operaton 2.0.x vs 2.1.x** 补丁号与 BOM 中 Spin / REST 精确 artifactId。
- **spring-ai-alibaba** + MCP starter 在 Boot 4 下的锁定版本组合。
- `camunda` 分支是否需要在 README 中声明「仅接受安全修复、不接收新功能」的维护策略。
