## Why

cryoEMS 单颗粒 movie 流水线已迁至 Kiwi-admin（Camunda）编排（见 `cyroems-kiwi-workflow-integration`），但 tomo 数据集所走的 **mdoc 流水线** 仍由 `cryoems-web-server` 内的 `MdocEngine` + `FlowManager.getMDocFlow` + 一组 `Handler<MDocContext>` 在本进程中顺序推进。这与 movie 的"以 Kiwi 为唯一编排权威"模式不一致，且 mdoc 的步骤之一 `MdocMotionWait` 跨实例依赖 movie 的处理结果，本地链路难以与远端流程协同。本 change 把 mdoc 也对齐到 movie 的最小闭环，并在此次同时打通"BPMN UserTask 卡点 + cryoEMS 轮询推动"的握手通路。

## What Changes

- **cryoEMS（cryo-web-server）侧的 mdoc 调度**：`MdocEngine` 不再持有本地 `InstanceProcessor`/`IFlow`/Handler 链路；改为按批选取 `MDocInstance` 后调用 `MdocKiwiWorkflowService.ensureStarted(...)` 在 Kiwi 中启动 mdoc 流程，并把 `external_workflow_instance_id` 写回 `MDocInstance`。
- **共享 watcher**：`WorkflowIntegrationConfiguration` 中将原 `movieKiwiWorkflowInstanceWatcher` bean **改名** 为通用的 `kiwiWorkflowInstanceWatcher`；同时注册 `MovieKiwiWorkflowStateSyncListener` 与新增的 `MdocKiwiWorkflowStateSyncListener`，两者通过自身 repository 隐式过滤实例归属。
- **MotionWait → UserTask 握手**：BPMN 在等待前置 movie 处理完成的位置使用 **UserTask（约定 `activityId = "mdoc-motion-wait"`）**；`MdocKiwiWorkflowStateSyncListener` 在 `onPoll` 内识别该活动后扫描对应 `MDoc.meta.tilts[].dataId` 对应的 `MovieResult.motion` 完成度，齐则调用 `KiwiWorkflowClient.completeUserTask(instanceId, taskKey, vars)` 推动流程继续。
- **`KiwiWorkflowClient` 扩展**：新增 `completeUserTask(instanceId, taskKey, completionVars)`，调用 kiwi-admin 新端点 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`。
- **kiwi-admin 机机端点**：新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`，按 instanceId + taskDefinitionKey 定位 active UserTask 并以入参中的 variables 完成；找不到（实例已不在 active 或 task 已不存在）时返回稳定可识别的语义错误（4xx）。
- **`MDocInstance` 字段扩展**：新增 `external_workflow_instance_id`、`currentActivity`（与 `Movie` 同形）。
- **`MDocInstanceRepository`**：新增按 `external_workflow_instance_id`（单/批）查询方法、`restore(List<String> ids)` 重置方法。
- **`KiwiWorkflowProperties`**：新增 `mdocBatchSize` 等 mdoc 调度项；与 `cryoems-task-workflow-selection` 引入的 `process-types.mdoc.*` 配置形成"任务级 → type 默认 → （历史）全局"的三段回退（解析职责在 `MdocKiwiWorkflowService`）。
- **BPMN 占位骨架**：在 `cryo-web-server/src/main/resources/assets/` 或 `cryoems-bpm` 的 samples 目录提供 `cryo-mdoc-minimal.bpmn`，含 `Start → ServiceTask:placeholder → UserTask(activityId=mdoc-motion-wait) → ServiceTask:placeholder → End`，用于联调启动 + 状态同步 + UserTask 推动闭环。
- **BREAKING：硬删旧本地链路**：删除 `MDocProcessor`、`MDocContext`、`MDocStep`、`FlowManager.getMDocFlow(Task)`，以及所有 `Handler<MDocContext>` 实现（`MDocParseHandler`、`task/tilt/movie/MotionWait`、`task/tilt/movie/MovieConnect`、`MdocStackHandler`、`ExcludeHandler`、`CoarseAlign`、`PatchTracking`、`SeriesAlign`、`AlignRecon`、`MdocSlurmStepHandler`）以及 `HandlerKey` 中仅被这些 handler 引用的枚举（`MDocInit`、`MdodParser`、`MdocMotionWait`、`MovieConnect`、`MdocStack`、`MdocExclude`、`MdocCoarseAlign`、`MdocPatchTracking`、`MdocSeriesAlign`、`AlignRecon`、`MDOC_SLURM`）。`MDocExportContext`/`MDocExportHandler`/`ExportMdocEngine` 等 mdoc 导出链路与本次硬删无关，保持现状。

## Capabilities

### New Capabilities
- `cryoems-mdoc-workflow-integration`: cryo-em-server-backend 端 mdoc 流水线"启动 Kiwi 流程 + 同步状态 + 通过 UserTask 完成 MotionWait 握手"的完整能力，含 `MdocEngine` 重写、共享 watcher 改名、`MdocKiwiWorkflowService`/`MdocKiwiWorkflowStateSyncListener`、`MDocInstance` 字段与 repository 扩展、`KiwiWorkflowClient.completeUserTask` 客户端调用，以及对旧本地链路（`MDocContext` 等）的硬删除。
- `kiwi-bpm-user-task-complete`: kiwi-admin 端按 `instanceId + taskDefinitionKey` 完成 active UserTask 的机机集成端点（`POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`），含鉴权（PAT 兑换的 Sa-Token）、入参 variables 透传、找不到/重复匹配/已结束的错误语义。

### Modified Capabilities
<!-- 仓库当前已归档的 spec 仅 slurm-workdir-cleanup，与本次改动无关；其他改动仅修改 in-progress change 中尚未归档的能力，故此处为空 -->

## Impact

- **cryo-em-server-backend (cryo-web-server)**：
  - 新增 `com.cryo.integration.workflow.MdocKiwiWorkflowService`、`MdocKiwiWorkflowVariables`、`MdocKiwiWorkflowStateSyncListener`。
  - 改写 `com.cryo.task.tilt.MdocEngine`（删除 `InstanceProcessor`/`IFlow`/`FlowManager` 注入，新增 `ensureStarted` 调度循环；与 `MovieEngine` 形状一致）。
  - `com.cryo.model.tilt.MDocInstance` 新增 `external_workflow_instance_id`、`currentActivity`；`com.cryo.dao.MDocInstanceRepository` 新增 by-instance-id / restore by-ids 查询。
  - `com.cryo.integration.workflow.WorkflowIntegrationConfiguration` 的 watcher bean **重命名** `movieKiwiWorkflowInstanceWatcher → kiwiWorkflowInstanceWatcher`；`MovieEngine` 中所有 `@Qualifier("movieKiwiWorkflowInstanceWatcher")` 与 `applicationContext.getBean("movieKiwiWorkflowInstanceWatcher", …)` 同步替换为新名。
  - `com.cryo.integration.workflow.KiwiWorkflowClient` 新增 `completeUserTask(...)` 方法。
  - `com.cryo.integration.workflow.KiwiWorkflowProperties` 新增 `mdocBatchSize`；与 `cryoems-task-workflow-selection` 中 `process-types.mdoc.*` 三段回退由 `MdocKiwiWorkflowService.resolveMdocBpmProcessId` 合并实现。
  - 硬删上文列出的 `MDocContext`/`MDocProcessor`/`MDocStep`/`FlowManager.getMDocFlow` 以及 10 个 `Handler<MDocContext>` 实现及对应 `HandlerKey` 枚举值。`com.cryo.task.tilt.export.MDocExportHandler` 中残留的 `import com.cryo.task.tilt.MDocContext;` 一并清理。
  - `application.yml`：新增 `app.kiwi.workflow.mdoc-batch-size` 示例与注释，说明 mdoc 流程 id 来源（Task → process-types.mdoc → 历史回退）。
  - 资源：新增占位 BPMN `assets/cryo-mdoc-minimal.bpmn`。
- **kiwi-admin**：
  - `kiwi-admin/backend` 新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 端点（与 movie 启动端点同属 BPM 集成 controller，复用既有 Sa-Token 鉴权）。
- **前置/并行依赖**：
  - 本 change 依赖 `cryoems-task-workflow-selection` 提供的 `Task.mdocProcessDefinitionId` 字段与 `process-types.mdoc.*` 配置；若该 change 尚未落地，`MdocKiwiWorkflowService` 在解析流程 id 时仅按 Task 字段（若已存在）+ 历史 `movie-process-definition-id` 回退处理，不阻塞本次启动+状态同步通路。
- **不影响**：`MovieEngine`/`MovieKiwiWorkflowService` 的运行时行为（仅 bean 注入名同步替换）；mdoc 导出链路（`ExportMdocEngine`/`MDocExportContext`/`MDocExportHandler`）；Camunda 引擎部署。
