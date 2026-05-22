## ADDED Requirements

### Requirement: MDocInstance 持有 Kiwi 流程实例关联
cryo-em-server-backend 的 `com.cryo.model.tilt.MDocInstance` SHALL 拥有 `external_workflow_instance_id`（字符串，可空）与 `currentActivity`（字符串，可空）两个字段，分别保存 Kiwi/Camunda 流程实例 id 与当前展示的活动节点名/id；其语义 MUST 与 `com.cryo.model.Movie` 上同名字段一致（参见 movie 同等字段：`external_workflow_instance_id` 用于关联远端实例，`currentActivity` 在终态后清空、ERROR 状态下取 errorActivity）。

#### Scenario: 字段持久化
- **WHEN** `MdocKiwiWorkflowService.ensureStarted(...)` 成功调用 Kiwi 启动接口并拿到 `instanceId`
- **THEN** 对应 `MDocInstance` 的 `external_workflow_instance_id` MUST 被写入 `instanceId`，并通过 `mongoTemplate.save(instance)` 持久化

#### Scenario: 终态清空 currentActivity
- **WHEN** `MdocKiwiWorkflowStateSyncListener.onInstanceTerminal` 收到 `state="COMPLETED"` 或 `state="CANCELED"` 的最终状态
- **THEN** 对应 `MDocInstance.currentActivity` MUST 被清空（设为 `null`）

#### Scenario: ERROR 期间的 currentActivity 取值
- **WHEN** Kiwi 流程实例进入 ERROR，`KiwiProcessInstanceState.errorActivityName` 非空
- **THEN** `MDocInstance.currentActivity` MUST 写入 `errorActivityName`（若 `errorActivityName` 为空则回退到 `errorActivityId`、`currentActivityName`、`currentActivityId` 的顺序，与 movie listener 同形）

### Requirement: MDocInstanceRepository 支持按 Kiwi 实例 id 查询与批量重置
cryo-em-server-backend 的 `com.cryo.dao.MDocInstanceRepository` SHALL 暴露以下查询方法：`Optional<MDocInstance> findByExternalWorkflowInstanceId(String instanceId)`、`List<MDocInstance> findByExternalWorkflowInstanceIdIn(Collection<String> instanceIds)`。已有的 `restore(List<String> ids)` 方法 MUST 同时 `$unset` `external_workflow_instance_id` 与 `process_status`，与 `MovieRepository.restore` 同形。

#### Scenario: 按 instance id 单条查询
- **WHEN** 调用 `findByExternalWorkflowInstanceId("abc")`
- **AND** Mongo `mDocInstance` 集合中存在恰好一条 `external_workflow_instance_id == "abc"` 的文档
- **THEN** 返回 `Optional.of(thatDoc)`

#### Scenario: 按 instance id 批量查询
- **WHEN** 调用 `findByExternalWorkflowInstanceIdIn(List.of("abc","def","xyz"))`
- **AND** Mongo 中 `abc`、`def` 命中，`xyz` 未命中
- **THEN** 返回的 List 长度为 2，包含 `abc`、`def` 对应文档

#### Scenario: restore 同时清理 external id
- **WHEN** 调用 `restore(List.of("id1","id2"))`
- **THEN** 这两条文档 MUST 被设置 `current_step="MDocInit"`、`forceReset=true`，并 `$unset` `error`、`cmds`、`steps`、`external_workflow_instance_id`、`process_status`

### Requirement: MdocKiwiWorkflowService 提供启动门面
cryo-em-server-backend SHALL 暴露 `com.cryo.integration.workflow.MdocKiwiWorkflowService`，至少提供 `boolean isMdocPipelineReady(Task task)`、`int getMdocBatchSize()`、`void ensureStarted(MDocInstance instance, MDoc mDoc, Task task, TaskDataset taskDataset) throws Exception` 三个方法；语义 MUST 与 `MovieKiwiWorkflowService` 对应方法同形（除参数中以 `MDocInstance + MDoc` 替代 `Movie`）。

#### Scenario: 客户端未就绪时启动跳过
- **WHEN** `KiwiWorkflowClient.isClientConfigured()` 返回 false
- **OR** `resolveMdocBpmProcessId(task)` 返回 null/空
- **THEN** `ensureStarted` MUST 直接返回，不发起 HTTP 请求，不修改 `MDocInstance`

#### Scenario: 成功启动并持久化关联 id
- **WHEN** 客户端已就绪且能解析到 `bpmProcessId`
- **AND** `KiwiWorkflowClient.startProcess(bpmId, vars)` 返回 `"inst-123"`
- **THEN** `MDocInstance.external_workflow_instance_id` 被设为 `"inst-123"`，`process_status.processing` 设为 true、`process_status.processing_at` 设为当前时间，并被 `mongoTemplate.save` 持久化
- **AND** 共享 `kiwiWorkflowInstanceWatcher.track("inst-123")` MUST 被调用

#### Scenario: 启动变量为瘦契约
- **WHEN** 调用 `MdocKiwiWorkflowVariables.from(instance, mDoc, task, taskDataset).toMap()`
- **THEN** 返回的 Map 顶层 MUST 仅含 `task` 与 `mdoc` 两个键
- **AND** `task` MUST 至少含 `id`（即 `task.getId()`），`mdoc` MUST 至少含 `id`（`instance.getId()`）与 `dataId`（`instance.getData_id()`，即对应 `MDoc._id`）

#### Scenario: 流程 id 三段回退
- **WHEN** `resolveMdocBpmProcessId(task)` 被调用
- **THEN** 解析顺序 MUST 为：① `task.getMdocProcessDefinitionId()`（若 `cryoems-task-workflow-selection` 已落地）；② 配置 `app.kiwi.workflow.process-types.mdoc` 下按数据集 `is_tomo` 选 `defaultIdTomo` / `defaultIdNonTomo`（若已配置）；③ `app.kiwi.workflow.movie-process-definition-id`（迁移期回退）；任一非空即返回；全部为空返回 null

### Requirement: 共享 KiwiWorkflowInstanceWatcher 与多 listener 注册
cryo-em-server-backend 的 `com.cryo.integration.workflow.WorkflowIntegrationConfiguration` SHALL 暴露名为 `kiwiWorkflowInstanceWatcher` 的 `KiwiWorkflowInstanceWatcher` bean（替代原 `movieKiwiWorkflowInstanceWatcher`），并在构造时同时注册 `MovieKiwiWorkflowStateSyncListener` 与 `MdocKiwiWorkflowStateSyncListener` 两个监听器。`MovieEngine`、`MovieKiwiWorkflowService` 等所有原引用 `movieKiwiWorkflowInstanceWatcher` 的位置 MUST 同步更新为新 bean 名。

#### Scenario: bean 名变更
- **WHEN** Spring 启动
- **THEN** `ApplicationContext.getBean("kiwiWorkflowInstanceWatcher", KiwiWorkflowInstanceWatcher.class)` MUST 成功返回单例
- **AND** `ApplicationContext.getBean("movieKiwiWorkflowInstanceWatcher", ...)` MUST 抛 `NoSuchBeanDefinitionException`

#### Scenario: listener 隐式按归属过滤
- **WHEN** `watcher.poll()` 返回的 snapshot 含某 instanceId
- **AND** 该 instanceId 在 `MovieRepository` 中找不到、在 `MDocInstanceRepository` 中能找到
- **THEN** `MovieKiwiWorkflowStateSyncListener.onPoll` MUST 跳过该条目（不写库）
- **AND** `MdocKiwiWorkflowStateSyncListener.onPoll` MUST 处理该条目并更新对应 `MDocInstance`

### Requirement: MdocKiwiWorkflowStateSyncListener 同步状态与推动 MotionWait
cryo-em-server-backend SHALL 暴露 `com.cryo.integration.workflow.MdocKiwiWorkflowStateSyncListener`，实现 `KiwiWorkflowBatchWatchListener`；其 `onPoll`、`onInstanceTerminal`、`onInstanceNotFound` 三个方法对 `MDocInstance` 的字段更新规则 MUST 与 `MovieKiwiWorkflowStateSyncListener` 对 `Movie` 的同名规则一致（含 `currentActivity` 写入、`status` 转换、`process_status.processing` 切换、ERROR 计数与 `permanent` 标记、`restore` 触发、`current_step` 在 COMPLETED 时设为 `FINISHED_STEP`）。

#### Scenario: 实例 RUNNING 中 currentActivity 写回
- **WHEN** `onPoll` 收到 `state="RUNNING"`、`currentActivityName="mdoc-motion-wait"` 的实例
- **AND** 对应 `MDocInstance` 在 mongo 中存在
- **THEN** 该 `MDocInstance.currentActivity` MUST 被写为 `"mdoc-motion-wait"`，并通过 `mongoTemplate.save` 持久化

#### Scenario: 实例 NOT_FOUND 触发 restore
- **WHEN** `onInstanceNotFound(["inst-1","inst-2"])` 被回调
- **THEN** `MDocInstanceRepository.restore(...)` MUST 被以这两个 instance id 对应的 `MDocInstance.id` 列表调用，且日志记录 restored 信息

#### Scenario: MotionWait readiness 满足时主动 complete
- **WHEN** `onPoll` 处理某条 `MDocInstance` 时识别到 `currentActivityId == "mdoc-motion-wait"`
- **AND** 该实例对应的 `MDoc.meta.tilts[]` 的所有 `dataId` 在 `MovieResult` 集合中都能查到非空 `motion.predict_dose`
- **THEN** listener MUST 调用 `KiwiWorkflowClient.completeUserTask(instanceId, "mdoc-motion-wait", Map.of())`

#### Scenario: MotionWait readiness 未满足
- **WHEN** `onPoll` 识别到 `currentActivityId == "mdoc-motion-wait"`
- **AND** 至少一个 tilt 的 `MovieResult.motion.predict_dose` 缺失
- **THEN** listener MUST NOT 调用 `completeUserTask`，且不抛异常

#### Scenario: complete 失败的容错
- **WHEN** `completeUserTask` 调用抛异常（如 404/409）
- **THEN** listener MUST 捕获并打 warn 日志，不影响后续 listener 的处理；下一轮 `onPoll` 仍可重试

### Requirement: MdocEngine 仅承担"按批启动 Kiwi 流程"调度
cryo-em-server-backend 的 `com.cryo.task.tilt.MdocEngine` SHALL 不再持有 `com.cryo.task.engine.InstanceProcessor`、`com.cryo.task.engine.flow.IFlow`、`com.cryo.task.engine.flow.FlowManager` 等本地推进依赖；改为：定时调度时，先检查 `MdocKiwiWorkflowService.isMdocPipelineReady(task)`，再按 `getMdocBatchSize()` 取一批未启动的 `MDocInstance`，对每条调用 `MdocKiwiWorkflowService.ensureStarted(...)`；启动时若 Kiwi 客户端未就绪，`start()` MUST 抛 `IllegalStateException` 阻止 engine 运行（与 `MovieEngine` 同形）。

#### Scenario: 客户端未就绪时 start 失败
- **WHEN** `MdocEngine.start()` 被调用
- **AND** `MdocKiwiWorkflowService.isMdocPipelineReady(task)` 返回 false
- **THEN** MUST 抛 `IllegalStateException`，且 engine 不进入 running 状态

#### Scenario: 启动时追踪历史在跑的实例
- **WHEN** `MdocEngine.start()` 成功
- **AND** mongo 中存在 `task_id=本 task` 且 `process_status.processing=true` 的 `MDocInstance`
- **THEN** 所有其 `external_workflow_instance_id` 非空的 instance id MUST 被批量 `kiwiWorkflowInstanceWatcher.track(...)`

#### Scenario: 每轮按批启动
- **WHEN** 定时调度触发 `MdocEngine.handle(task)`
- **THEN** MUST 取最多 `getMdocBatchSize()` 条 `process_status.processing != true` 且 `current_step != FINISHED` 的 `MDocInstance`，按 `file_create_at` 升序
- **AND** 对每条调用 `MdocKiwiWorkflowService.ensureStarted(instance, mdoc, task, taskDataset)`，异常被捕获后继续下一条

### Requirement: KiwiWorkflowClient 支持 UserTask 完成调用
cryo-em-server-backend 的 `com.cryo.integration.workflow.KiwiWorkflowClient` SHALL 提供 `void completeUserTask(String instanceId, String taskKey, Map<String,Object> variables) throws Exception` 方法，HTTP 调用 kiwi-admin 的 `POST {baseUrl}/bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`，Body 为 `{"variables": <variables 或 {}>}`，鉴权头复用 `kiwiClient.authorizationHeader()`。对 HTTP 4xx/5xx 响应 MUST 抛 `IllegalStateException` 并在异常消息中携带状态码与响应体；不重试。

#### Scenario: 客户端未就绪
- **WHEN** `KiwiWorkflowClient.isClientConfigured()` 返回 false
- **AND** 调用 `completeUserTask("i","k", null)`
- **THEN** MUST 抛 `IllegalStateException` 并附明确"client is not configured"消息

#### Scenario: 成功 complete
- **WHEN** kiwi-admin 返回 2xx
- **THEN** 方法 MUST 正常返回（void），并以 info 级别日志记录 `instanceId`、`taskKey`

#### Scenario: 404 / 409 错误透传
- **WHEN** kiwi-admin 返回 404 或 409
- **THEN** MUST 抛 `IllegalStateException`，异常消息 MUST 含 HTTP 状态码与响应 body

### Requirement: KiwiWorkflowProperties 新增 mdoc 调度项
cryo-em-server-backend 的 `com.cryo.integration.workflow.KiwiWorkflowProperties` SHALL 新增 `int mdocBatchSize`（默认 5），可由 `app.kiwi.workflow.mdoc-batch-size` 配置。`getMdocBatchSize()` 返回值 MUST 至少为 1（小于等于 0 时按 1 处理）。

#### Scenario: 默认值
- **WHEN** `application.yml` 未配置 `app.kiwi.workflow.mdoc-batch-size`
- **THEN** `properties.getMdocBatchSize()` 返回 5

#### Scenario: 显式配置
- **WHEN** `app.kiwi.workflow.mdoc-batch-size: 12`
- **THEN** `properties.getMdocBatchSize()` 返回 12

#### Scenario: 非法值兜底
- **WHEN** `app.kiwi.workflow.mdoc-batch-size: 0`
- **AND** 调用方通过 `MdocKiwiWorkflowService.getMdocBatchSize()` 读取
- **THEN** 返回 `Math.max(0, 1) == 1`

### Requirement: 硬删旧本地 mdoc 推进链路
cryo-em-server-backend 的 `cryo-web-server` 模块 SHALL 在本 change 落地时删除以下类与符号：`com.cryo.task.tilt.MDocProcessor`、`com.cryo.task.tilt.MDocContext`、`com.cryo.task.tilt.MDocStep`、`com.cryo.task.engine.flow.FlowManager.getMDocFlow(Task)` 方法，以及所有 `Handler<MDocContext>` 实现（`com.cryo.task.tilt.parse.MDocParseHandler`、`com.cryo.task.tilt.movie.MotionWait`、`com.cryo.task.tilt.movie.MovieConnect`、`com.cryo.task.tilt.stack.MdocStackHandler`、`com.cryo.task.tilt.filter.ExcludeHandler`、`com.cryo.task.tilt.align.CoarseAlign`、`com.cryo.task.tilt.patchtracking.PatchTracking`、`com.cryo.task.tilt.seriesalign.SeriesAlign`、`com.cryo.task.tilt.recon.AlignRecon`、`com.cryo.task.tilt.MdocSlurmStepHandler`），以及 `com.cryo.task.engine.HandlerKey` 中仅被这些 handler 引用的枚举值（`MDocInit`、`MdodParser`、`MdocMotionWait`、`MovieConnect`、`MdocStack`、`MdocExclude`、`MdocCoarseAlign`、`MdocPatchTracking`、`MdocSeriesAlign`、`AlignRecon`、`MDOC_SLURM`）。`MDocExportHandler` 中残留的 `import com.cryo.task.tilt.MDocContext;` MUST 一并清理。`MDocExportContext`、`MDocExportHandler`、`ExportMdocEngine`、`HandlerKey.MDOC_EXPORT` 等导出链路 MUST NOT 被本次删除。

#### Scenario: 编译期所有引用消除
- **WHEN** 应用本次 change 全部代码改动
- **THEN** `cryo-web-server` 项目 `mvn compile` MUST 成功，且工作区中 `Grep MDocContext` 命中数为 0、`Grep MDocProcessor` 命中数为 0、`Grep MDocStep` 命中数为 0

#### Scenario: 导出链路不受影响
- **WHEN** 应用本次 change 全部代码改动
- **THEN** `com.cryo.task.export.MDocExportContext`、`com.cryo.task.tilt.export.MDocExportHandler`、`com.cryo.task.export.ExportMdocEngine` 三个类 MUST 仍然存在且编译通过

### Requirement: 占位 BPMN 骨架可用于联调
cryo-em-server-backend 的资源目录 SHALL 新增一份占位 BPMN 文件 `cryo-mdoc-minimal.bpmn`（与 `cryo-movie-minimal.bpmn` 同目录），包含一条线性流程 `StartEvent → ServiceTask(占位,初始化) → UserTask(id="mdoc-motion-wait") → ServiceTask(占位,继续) → EndEvent`，可直接在 Kiwi-admin 导入并部署，用于联调启动 + 状态同步 + UserTask complete 的最小闭环。

#### Scenario: 文件存在且可解析
- **WHEN** 应用本次 change
- **THEN** 资源目录 MUST 含 `cryo-mdoc-minimal.bpmn`
- **AND** 文件 MUST 可被 Camunda BPMN 解析器加载（XML 合法，含 `<bpmn:process id="..." isExecutable="true">`）

#### Scenario: UserTask id 约定
- **WHEN** 解析 `cryo-mdoc-minimal.bpmn`
- **THEN** 流程中 MUST 恰好存在一个 `bpmn:userTask`，其 `id` 属性 MUST 为 `"mdoc-motion-wait"`
