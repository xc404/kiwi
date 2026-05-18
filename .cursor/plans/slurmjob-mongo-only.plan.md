---
name: ""
overview: ""
todos: []
isProject: false
---

# Slurm sacct 跟踪：仅 Mongo（修订）

## 相对上一版的变更

- **不再**提供「Repository 与内存 `ConcurrentHashMap` 二选一」或 `persistenceEnabled=false` 回退内存的实现。
- **仅**通过 `**SlurmJobRepository`**（`MongoRepository<SlurmJob, String>`）读写跟踪数据；`SlurmJobCompletionTracker` **必须**依赖该 Repository（构造注入，无 `ObjectProvider` 内存分支）。

## 前提与装配

- 启用 `**kiwi.bpm.slurm.sacct.enabled=true`** 时，运行进程须具备 **Mongo**（存在 `MongoTemplate` / Spring Data Mongo 配置），否则 Slurm 自动配置在创建依赖 Repository 的 Bean 时应 **无法装配**（与「只用库」一致：不静默回退内存）。
- `**kiwi.bpm.slurm.sacct.enabled=false`** 时：不启动 sacct 调度；可不使用跟踪表（`registerAfterSubmit` 仍为 no-op）；此时若仍注册 `SlurmJobCompletionTracker`，可对 Repository 的依赖通过 **仅当 sacct 开启时注册 Tracker** 或 **Tracker 内 sacct 关则不调 repo** 等方式避免强绑 Mongo——实现时二选一写清：**推荐** `SlurmJobCompletionTracker` 仍注入 `SlurmJobRepository`，sacct 关闭时 tracker 不执行任何 `save/find/delete`（与现状「关闭 sacct 不轮询」一致），这样 **Mongo 仍可为其它功能存在**，不要求「关 sacct 也必须无 Mongo」。

（若产品要求「关 sacct 的应用绝对不能依赖 Mongo」：再把 Tracker Bean 改为 `@ConditionalOnProperty(sacct.enabled=true)`；本计划默认 **关 sacct 时不访问库**。）

## 实体与仓库（不变部分）

- 改造 `[SlurmJob](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmJob.java)`：`@Document`、`externalTaskId`、`workerId`、`registeredAtMillis`，`id` 与 `jobId` 一致。
- `SlurmJobRepository` + `SlurmJobCompletionTracker` 全量用库：注册 `save`、轮询/超时 `find*`、终态 `deleteById`。
- 删除 `[SlurmTrackedExternalJob](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmTrackedExternalJob.java)` 记录类型，逻辑并入 `SlurmJob`。

## 依赖与测试

- `kiwi-bpmn-component` 显式 `spring-boot-starter-data-mongodb`。
- 测试：**Mock `SlurmJobRepository`**（或 `@DataMongoTest` 择一），不再测试「无库走 Map」分支。

## 弃用 flag 文件、全面改用 sacct 作为唯一终态来源

### 目标

- 将「监听工作目录 `*.flag` 完成外部任务」的方式 **标记为 `@Deprecated`（Javadoc 说明迁移到 sacct）**，并在行为上 **不再作为默认路径**。
- **Slurm 作业结束一律由 `sacct` 轮询**（`[SlurmJobCompletionTracker](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmJobCompletionTracker.java)` + `[SlurmSacctParser](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmSacctParser.java)`）驱动与现有一致的 Camunda 终态（`[SlurmFlagFileHandler#processParsedSlurmTerminal](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmFlagFileHandler.java)` 可保留为 **终态上报实现**，类名可后续再议，避免与「flag」语义绑定）。

### 建议弃用范围（实现时打 `@Deprecated` + `@deprecated` 说明）

- `[SlurmTaskManager#startFlagWatcher](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmTaskManager.java)`、`FileAlterationMonitor` / `FileAlterationObserver` 注册 `[SlurmFlagFileHandler](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmFlagFileHandler.java)` 为 listener 的逻辑；**默认不再调用** `startFlagWatcher`（或调用即 no-op + 单次 warn）。
- `appendFlagCompletionToSbatch` / `buildFlagCompletionShellLine`：不再向 sbatch 追加写 `$SLURM_JOB_ID.flag` 的 `printf` 行（与 flag 机制绑定，一并弃用）。
- `[SlurmService#createSbatchFile](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmService.java)` 内为 flag 服务的 `__KIWI_SLURM_CMD_EC` 子 shell 包装：若 sacct 以 `.batch` 的 `ExitCode` 为准，可简化为直接执行用户命令（仍建议 `set +e` 等由产品确认）；**弃用与 flag 强耦合的片段**并在 Javadoc 指向 sacct。
- 可选：`[SlurmFlagFileHandler#onFileCreate](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmFlagFileHandler.java)` 整类或该方法标记弃用；若短期需兼容旧集群仍写 flag，可用配置 `**kiwi.bpm.slurm.flag-listener-enabled`**（默认 `false`）单独打开监听（与「默认全非 flag」一致）。

### 配置与默认行为

- `**kiwi.bpm.slurm.sacct.enabled`**：与「全部 sacct」对齐，建议默认 `**true**`（或与 Slurm 总开关联动：Slurm 启用且未显式关 sacct 则开启）；文档写明 **依赖 Mongo 持久化跟踪行**（见上文）。
- 弃用 flag 后，**不再依赖**共享盘上 flag 出现；部署文档强调 submit 机须能执行 `sacct` 且记账可见。

### 行为与幂等

- 现有 `[SlurmExternalTaskTerminalCoordinator](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmExternalTaskTerminalCoordinator.java)` 仍防止重复 complete；仅 sacct 单路径后逻辑更简单。
- `[SlurmCmdResult](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmCmdResult.java)` 继续作为 sacct 解析结果到 Camunda 的 DTO，无需弃用。

### 测试与清理

- 删除或改写依赖 **文件监听 / 写 flag** 的用例；补充 **仅 sacct 解析 + Repository + `processParsedSlurmTerminal`** 的集成或单测。
- 工作目录清理 `[SlurmWorkdirCleanup](e:/Projects/kiwi/kiwi-bpmn/kiwi-bpmn-component/src/main/java/com/kiwi/bpmn/component/slurm/SlurmWorkdirCleanup.java)` 若仍包含 `.flag` 后缀，可保留删除规则（遗留文件）或标注仅清理历史文件。

## Todos

- `SlurmJob` @Document 字段与 `SlurmJobRepository`；AutoConfiguration；Tracker/TaskManager 仅 Mongo
- **默认关闭 flag 监听**；弃用 API（`startFlagWatcher`、sbatch 追加 flag、`onFileCreate` 路径等）；可选 `flag-listener-enabled` 兼容开关
- **默认开启 sacct**（与配置文档、SlurmProperties 默认值一致）；`submit` 后 `SlurmJob` 落库 + 轮询终态
- 精简 `SlurmService` 生成脚本（去掉仅为 flag 服务的片段，按 sacct 语义复核）
- `pom` + 单测：Mock Repository + sacct 终态路径；移除/改写 flag 相关测试

