## Why

cryoEMS 单颗粒 movie 流水线已迁至 Kiwi-admin（Camunda）编排（见 `cyroems-kiwi-workflow-integration`），但 tomo 数据集所走的 **mdoc 流水线** 仍由 `cryoems-web-server` 内的 `MdocEngine` + `FlowManager.getMDocFlow` + 一组 `Handler<MDocContext>` 在本进程中顺序推进。这与 movie 的"以 Kiwi 为唯一编排权威"模式不一致，且 mdoc 的步骤之一 `MdocMotionWait` 跨实例依赖 movie 的处理结果，本地链路难以与远端流程协同。本 change 把 mdoc 也对齐到 movie 的最小闭环，并在此次同时打通"BPMN 等待节点（ManualTask `asyncBefore` 或 UserTask）+ cryoEMS 轮询推动"的握手通路；旧本地链路代码以 `@Deprecated` 保留一版，下一次再硬删。

## What Changes

- **cryoEMS（cryo-web-server）侧的 mdoc 调度**：`MdocEngine` 不再持有本地 `InstanceProcessor`/`IFlow`/Handler 链路；改为按批选取 `MDocInstance` 后调用 `MdocKiwiWorkflowService.ensureStarted(...)` 在 Kiwi 中启动 mdoc 流程，并把 `external_workflow_instance_id` 写回 `MDocInstance`。
- **共享 watcher**：`WorkflowIntegrationConfiguration` 中将原 `movieKiwiWorkflowInstanceWatcher` bean **改名** 为通用的 `kiwiWorkflowInstanceWatcher`；同时注册 `MovieKiwiWorkflowStateSyncListener` 与新增的 `MdocKiwiWorkflowStateSyncListener`，两者通过自身 repository 隐式过滤实例归属。
- **MotionWait → ManualTask 握手**：BPMN 在等待前置 movie 处理完成的位置使用 **ManualTask**（`camunda:asyncBefore="true"`，由此在该节点前形成 async-continuation Job 作为外部推进点；同样的 endpoint 也兼容 UserTask 用法），其 `activityId` 由新增配置 `app.kiwi.workflow.mdoc-motion-wait-activity-id`（默认 `mdoc-motion-wait`）指定。**推动职责由独立调度器 `MdocMotionWaitScheduler` 承担**（替代原"挂在 listener.onPoll"的方案）：周期扫描 `Task.status=running` 且 `MDocInstance.currentActivity == motionWaitActivityId` 的实例，对每条扫描其 `MDoc.meta.tilts[].dataId` 对应的 `MovieResult.motion` 完成度，齐则调用 `KiwiWorkflowClient.complete(instanceId, configuredTaskKey, vars)` 推动流程继续。`MdocKiwiWorkflowStateSyncListener` 仅负责状态字段写回（`currentActivity` / `status` / `processing`），不再做 readiness 判定。
- **`KiwiWorkflowClient` 扩展**：新增 `complete(instanceId, taskKey, completionVars)`，调用 kiwi-admin 新端点 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`。
- **kiwi-admin 机机端点**：新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`，先按 instanceId + taskDefinitionKey 查 active UserTask 并 `TaskService.complete`；未命中再按 activityId 查 async-continuation Job 并 `ManagementService.executeJob`（覆盖 ManualTask `asyncBefore` 等以 Job 停泊的等待节点）；找不到任一可推进点或多条匹配时返回稳定可识别的语义错误（404/409）。
- **`MDocInstance` 字段扩展**：新增 `external_workflow_instance_id`、`currentActivity`（与 `Movie` 同形）。
- **`MDocInstanceRepository`**：新增按 `external_workflow_instance_id`（单/批）查询方法、`restore(List<String> ids)` 重置方法。
- **`KiwiWorkflowProperties`**：新增 `mdocBatchSize`、`mdocMotionWaitActivityId`（默认 `"mdoc-motion-wait"`）、`mdocMotionWaitPollIntervalMillis`（默认 10s）、`mdocMotionWaitInitialDelayMillis`（默认 30s）四个 mdoc 调度项；与 `cryoems-task-workflow-selection` 引入的 `process-types.mdoc.*` 配置形成"任务级 → type 默认 → （历史）全局"的三段回退（解析职责在 `MdocKiwiWorkflowService`）。
- **BPMN 占位骨架**：在 `cryo-web-server/src/main/resources/assets/` 或 `cryoems-bpm` 的 samples 目录提供 `cryo-mdoc-minimal.bpmn`，含 `Start → ServiceTask:placeholder → ManualTask(activityId 与 app.kiwi.workflow.mdoc-motion-wait-activity-id 一致，默认 mdoc-motion-wait，camunda:asyncBefore="true") → ServiceTask:placeholder → End`，用于联调启动 + 状态同步 + ManualTask 推动闭环。
- **旧本地链路标记 deprecated（不再硬删）**：保留 `MDocProcessor`、`MDocContext`、`MDocStep`、`FlowManager.getMDocFlow(Task)`，以及全部 `Handler<MDocContext>` 实现（`MDocParseHandler`、`task/tilt/movie/MotionWait`、`task/tilt/movie/MovieConnect`、`MdocStackHandler`、`ExcludeHandler`、`CoarseAlign`、`PatchTracking`、`SeriesAlign`、`AlignRecon`、`MdocSlurmStepHandler`）与 `FilePathService.getMdocWorkDir(MDocContext)`；统一加 `@Deprecated` 与 Javadoc 标注（HandlerKey 中对应的 10 项枚举：`MdodParser` / `MovieConnect` / `MdocMotionWait` / `MdocStack` / `MdocExclude` / `MdocCoarseAlign` / `MdocPatchTracking` / `MdocSeriesAlign` / `AlignRecon` / `MDOC_SLURM` 同样标记 `@Deprecated`，`MDocInit` 与 `MDOC_EXPORT` 仍在使用，未弃用）。`MdocEngine` 改造后不再注入/调用任何上述 deprecated 组件，硬删延后到下一版本。`MDocExportContext`/`MDocExportHandler`/`ExportMdocEngine` 等 mdoc 导出链路保持现状。

## Capabilities

### New Capabilities
- `cryoems-mdoc-workflow-integration`: cryo-em-server-backend 端 mdoc 流水线"启动 Kiwi 流程 + 同步状态 + 通过 ManualTask/UserTask 完成 MotionWait 握手"的完整能力，含 `MdocEngine` 重写、共享 watcher 改名、`MdocKiwiWorkflowService`/`MdocKiwiWorkflowStateSyncListener`、`MDocInstance` 字段与 repository 扩展、`KiwiWorkflowClient.complete` 客户端调用，以及对旧本地链路（`MDocContext` 等）的 `@Deprecated` 标记（保留代码、不再调度）。
- `kiwi-bpm-user-task-complete`: kiwi-admin 端按 `instanceId + taskKey` 推动等待节点的机机集成端点（`POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`），支持 UserTask（`taskService.complete`）与 ManualTask `asyncBefore` 等 Job 形式停泊节点（`managementService.executeJob`），含鉴权（PAT 兑换的 Sa-Token）、入参 variables 透传、找不到/重复匹配/已结束的错误语义。

### Modified Capabilities
<!-- 仓库当前已归档的 spec 仅 slurm-workdir-cleanup，与本次改动无关；其他改动仅修改 in-progress change 中尚未归档的能力，故此处为空 -->

## Impact

- **cryo-em-server-backend (cryo-web-server)**：
  - 新增 `com.cryo.integration.workflow.MdocKiwiWorkflowService`、`MdocKiwiWorkflowVariables`、`MdocKiwiWorkflowStateSyncListener`、`MdocMotionWaitScheduler`。
  - 改写 `com.cryo.task.tilt.MdocEngine`（删除 `InstanceProcessor`/`IFlow`/`FlowManager` 注入，新增 `ensureStarted` 调度循环；与 `MovieEngine` 形状一致）。
  - `com.cryo.model.tilt.MDocInstance` 新增 `external_workflow_instance_id`、`currentActivity`；`com.cryo.dao.MDocInstanceRepository` 新增 by-instance-id / restore by-ids 查询。
  - `com.cryo.integration.workflow.WorkflowIntegrationConfiguration` 的 watcher bean **重命名** `movieKiwiWorkflowInstanceWatcher → kiwiWorkflowInstanceWatcher`；`MovieEngine` 中所有 `@Qualifier("movieKiwiWorkflowInstanceWatcher")` 与 `applicationContext.getBean("movieKiwiWorkflowInstanceWatcher", …)` 同步替换为新名。
  - `com.cryo.integration.workflow.KiwiWorkflowClient` 新增 `complete(...)` 方法。
  - `com.cryo.integration.workflow.KiwiWorkflowProperties` 新增 `mdocBatchSize`、`mdocMotionWaitActivityId`、`mdocMotionWaitPollIntervalMillis`、`mdocMotionWaitInitialDelayMillis`；与 `cryoems-task-workflow-selection` 中 `process-types.mdoc.*` 三段回退由 `MdocKiwiWorkflowService.resolveMdocBpmProcessId` 合并实现。
  - `MDocContext`/`MDocProcessor`/`MDocStep`/`FlowManager.getMDocFlow` 以及 10 个 `Handler<MDocContext>` 实现 + `FilePathService.getMdocWorkDir(MDocContext)` 统一加 `@Deprecated`；`HandlerKey` 中对应的 10 项枚举同样加 `@Deprecated`（`MDocInit` 与 `MDOC_EXPORT` 仍在使用，未弃用）。`com.cryo.task.tilt.export.MDocExportHandler` / `com.cryo.task.export.ExportMdocEngine` 中残留的 `import com.cryo.task.tilt.MDocContext;` 保留原样（仅在 deprecated 旁路被使用）。
  - `application.yml`：新增 `app.kiwi.workflow.mdoc-batch-size`、`mdoc-motion-wait-activity-id`、`mdoc-motion-wait-poll-interval-millis`、`mdoc-motion-wait-initial-delay-millis` 示例与注释，说明 mdoc 流程 id 来源（Task → process-types.mdoc → 历史回退）。
  - 资源：新增占位 BPMN `assets/cryo-mdoc-minimal.bpmn`（ManualTask + `asyncBefore="true"`）。
- **kiwi-admin**：
  - `kiwi-admin/backend` 新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 端点（与 movie 启动端点同属 BPM 集成 controller，复用既有 Sa-Token 鉴权）；实现兼容 UserTask 与 ManualTask `asyncBefore` Job 两种推进语义。
- **前置/并行依赖**：
  - 本 change 依赖 `cryoems-task-workflow-selection` 提供的 `Task.mdocProcessDefinitionId` 字段与 `process-types.mdoc.*` 配置；若该 change 尚未落地，`MdocKiwiWorkflowService` 在解析流程 id 时仅按 Task 字段（若已存在）+ 历史 `movie-process-definition-id` 回退处理，不阻塞本次启动+状态同步通路。
- **不影响**：`MovieEngine`/`MovieKiwiWorkflowService` 的运行时行为（仅 bean 注入名同步替换）；mdoc 导出链路（`ExportMdocEngine`/`MDocExportContext`/`MDocExportHandler`）；Camunda 引擎部署。
