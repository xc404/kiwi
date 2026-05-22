## 1. kiwi-admin: 新增完成 UserTask 的机机端点

- [ ] 1.1 在 kiwi-admin BPM 集成 controller（与现有 `POST /bpm/process/{id}/start` 同类）新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 端点，请求体 `{"variables": Map<String,Object>?}`，返回与现有端点同形的业务响应包装
- [ ] 1.2 实现内部逻辑：用 `TaskService.createTaskQuery().processInstanceId(...).taskDefinitionKey(...).active().list()` 查找 active UserTask；空 → 404、>1 → 409、=1 → `TaskService.complete(taskId, variables ?? Map.of())`
- [ ] 1.3 鉴权复用现有 Sa-Token 机制（与 movie 启动端点同模式），确保 PAT 兑换的 token 可用
- [ ] 1.4 单元/集成测试：covers 成功、404（实例不存在/已结束）、409（无匹配 / 重复匹配）、401（未授权）、variables 为 null/空对象的兜底
- [ ] 1.5 更新 kiwi-admin 端 API 文档/Swagger，列入"BPM 集成"分组

## 2. cryo-em-server-backend: 模型与 Repository

- [ ] 2.1 `com.cryo.model.tilt.MDocInstance` 新增 `external_workflow_instance_id`（String）与 `currentActivity`（String）两个字段；保持 Lombok/Mongo 映射风格与 `Movie` 同形
- [ ] 2.2 `com.cryo.dao.MDocInstanceRepository` 新增 `Optional<MDocInstance> findByExternalWorkflowInstanceId(String)` 与 `List<MDocInstance> findByExternalWorkflowInstanceIdIn(Collection<String>)` 查询方法（与 `MovieRepository` 同形 `@Query` 注解）
- [ ] 2.3 更新已存在的 `MDocInstanceRepository.restore(List<String> ids)` 的 `@Update` 注解：`$unset` 中追加 `external_workflow_instance_id`、`process_status`，对齐 `MovieRepository.restore`

## 3. cryo-em-server-backend: KiwiWorkflowClient 与 Properties

- [ ] 3.1 `com.cryo.integration.workflow.KiwiWorkflowClient` 新增 `completeUserTask(String instanceId, String taskKey, Map<String,Object> variables) throws Exception` 方法：HTTP POST `{baseUrl}/bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`、Body `{"variables": variables ?? Map.of()}`、`Authorization` 头复用 `kiwiClient.authorizationHeader()`，超时取 `properties.getClient().getHttpRequestTimeoutSeconds()`
- [ ] 3.2 `completeUserTask` 对非 2xx 响应抛 `IllegalStateException`，异常消息含 HTTP 状态码与响应 body；不做内部重试
- [ ] 3.3 `com.cryo.integration.workflow.KiwiWorkflowProperties` 新增 `int mdocBatchSize = 5` 字段、Javadoc 注明用途
- [ ] 3.4 在 `application.yml` 的 `app.kiwi.workflow` 段下加入 `mdoc-batch-size: 5` 示例与中文注释（参考 `movie-batch-size` 注释风格）

## 4. cryo-em-server-backend: Mdoc 启动门面与变量

- [ ] 4.1 新增 `com.cryo.integration.workflow.MdocKiwiWorkflowVariables`，含静态工厂 `from(MDocInstance, MDoc, Task, TaskDataset)` 与 `toMap()`，瘦契约结构 `{ "task": { "id": ... }, "mdoc": { "id": ..., "dataId": ... } }`
- [ ] 4.2 新增 `com.cryo.integration.workflow.MdocKiwiWorkflowService`，注入 `KiwiWorkflowClient`、`KiwiWorkflowProperties`、`TaskRepository`、`TaskDataSetRepository`、`MDocInstanceRepository`、`MongoTemplate`、（共享）`KiwiWorkflowInstanceWatcher`、（注意 4.7 完成后）`MDocRepository`
- [ ] 4.3 实现 `isMdocPipelineReady(Task task)`：客户端配置 && `resolveMdocBpmProcessId(task)` 非空
- [ ] 4.4 实现 `getMdocBatchSize()`：`Math.max(properties.getMdocBatchSize(), 1)`
- [ ] 4.5 实现 `ensureStarted(MDocInstance instance, MDoc mDoc, Task task, TaskDataset taskDataset)`：组装变量 → `startProcess` → 写回 `instance.external_workflow_instance_id`、`process_status.processing=true`、`processing_at=now` → `mongoTemplate.save` → `watcher.track(instanceId)`
- [ ] 4.6 实现 `resolveMdocBpmProcessId(Task task)` 的三段回退：`task.mdocProcessDefinitionId` → `properties.processTypes.mdoc.defaultIdTomo/NonTomo`（按数据集 `is_tomo`） → `properties.movieProcessDefinitionId`；若 `cryoems-task-workflow-selection` 尚未落地（`task.mdocProcessDefinitionId` 字段/`processTypes` 配置不存在），仅以 (3) 兜底，并在代码注释中标注 TODO 链回该 change
- [ ] 4.7 Javadoc 与日志风格对齐 `MovieKiwiWorkflowService`

## 5. cryo-em-server-backend: 共享 Watcher 重命名

- [ ] 5.1 `com.cryo.integration.workflow.WorkflowIntegrationConfiguration` 中：将 `@Bean("movieKiwiWorkflowInstanceWatcher")` 改名为 `@Bean("kiwiWorkflowInstanceWatcher")`；构造方法参数新增 `MdocKiwiWorkflowStateSyncListener`，在返回前对该 listener 调用 `watcher.registerListener(...)`（与 movie listener 并列）
- [ ] 5.2 `com.cryo.task.movie.MovieEngine` 中 `applicationContext.getBean("movieKiwiWorkflowInstanceWatcher", KiwiWorkflowInstanceWatcher.class)` 改为 `getBean("kiwiWorkflowInstanceWatcher", ...)`，并更新字段名/局部变量
- [ ] 5.3 `com.cryo.integration.workflow.MovieKiwiWorkflowService` 上 `@Qualifier("movieKiwiWorkflowInstanceWatcher")` 改为 `@Qualifier("kiwiWorkflowInstanceWatcher")`
- [ ] 5.4 全仓搜索 `movieKiwiWorkflowInstanceWatcher`，确保 0 命中

## 6. cryo-em-server-backend: Mdoc 状态同步 + MotionWait 推动

- [ ] 6.1 新增 `com.cryo.integration.workflow.MdocKiwiWorkflowStateSyncListener implements KiwiWorkflowBatchWatchListener`，整体结构按 `MovieKiwiWorkflowStateSyncListener` 复制裁剪：用 `MDocInstanceRepository` 加载/批量加载/按 instance id 查回滚集，`Optional` 取单条，listener 内部按自身 repo 隐式过滤
- [ ] 6.2 实现 `applyState(MDocInstance, KiwiProcessInstanceState, boolean terminal)` 复用 movie 同形分支：RUNNING（`applyInFlight`）、ERROR（`applyWorkflowError`，含 `error_count` / `permanent` 标记）、COMPLETED（`applyCompleted`，设 `current_step=FINISHED_STEP`、清 `currentActivity`、`processing=false`）、CANCELED（`applyCanceled`）
- [ ] 6.3 实现 `onInstanceNotFound`：批量 `mdocInstanceRepository.restore(List<MDocInstance.id>)`，并按 movie 同形 log info
- [ ] 6.4 在 `onPoll` 内对每条已写库的 mdoc 实例，**在 `mongoTemplate.save` 之后**判断 `state.getCurrentActivityId() == "mdoc-motion-wait"`（或 `currentActivityName`）；命中则调用新私有方法 `triggerMotionWaitIfReady(MDocInstance, KiwiProcessInstanceState)`
- [ ] 6.5 `triggerMotionWaitIfReady` 逻辑：取 `mdocInstance.data_id` → `mDocRepository.findById(...)` → 取 `mdoc.meta.tilts[].dataId` 列表 → 用一次 `MovieResultRepository.findByQuery(Query.query(Criteria.where("movie_data_id").in(ids).and("config_id").is(task.getDefault_config_id())))` 查询 → 全部 `motion.predict_dose` 非空才视为就绪
- [ ] 6.6 就绪时调用 `kiwiWorkflowClient.completeUserTask(instanceId, "mdoc-motion-wait", Map.of())`；任何异常（4xx/5xx/网络）捕获后打 warn，不抛出
- [ ] 6.7 抽取常量 `MDOC_MOTION_WAIT_ACTIVITY_ID = "mdoc-motion-wait"` 放在 `MdocKiwiWorkflowStateSyncListener` 类静态字段或专门常量类，避免在多个位置硬编码

## 7. cryo-em-server-backend: 改写 MdocEngine

- [ ] 7.1 删除 `MdocEngine` 中 `InstanceProcessor`、`IFlow`、`FlowManager`、`MDocRepository`（用于 process 内执行所需的）等本地推进相关字段与构造注入；保留对 `TaskDataSetRepository`、`MDocInstanceRepository`、`TaskStatistic`、`TaskScheduler` 的依赖；新增对 `MdocKiwiWorkflowService`、共享 `KiwiWorkflowInstanceWatcher`、`MDocRepository`（用于 `ensureStarted` 参数组装）的注入
- [ ] 7.2 `start()` 前置校验：`if (!mdocKiwiWorkflowService.isMdocPipelineReady(task)) throw new IllegalStateException(...)`（消息含可读引导：配置 `app.kiwi.workflow.enabled` / PAT / Task 上的 `mdocProcessDefinitionId`）
- [ ] 7.3 `start()` 调用 `trackProcessingWorkflowInstances()` 私有方法：扫 `task_id=本 task && process_status.processing=true` 的 `MDocInstance`，对 `external_workflow_instance_id` 非空者批量 `watcher.track(ids)`
- [ ] 7.4 `handle(task)` 改写：移除 `idleCount` / `instanceProcessor.submit` / `IFlow` 相关逻辑；取批大小 `mdocKiwiWorkflowService.getMdocBatchSize()`，查未启动的 `MDocInstance`（条件复用 `InstanceRepository.unprocessed()` + `task_id`），按 `file_create_at` 升序限批；对每条加载对应 `MDoc` 并调用 `mdocKiwiWorkflowService.ensureStarted(instance, mdoc, task, taskDataset)`，单条异常打 error 后继续下一条；最后调 `taskStatistic.statisticMDoc(task)`
- [ ] 7.5 删除 `MdocEngine.updateProcessingStatus()`、`resetProcessing()` 中依赖 IFlow / processor 的部分；如不再需要则一并删除（与 movie 同形：Watcher 终态会清 processing）
- [ ] 7.6 `TaskMonitor` 不需改动（它已经在 `dataset.getIs_tomo()` 时构造 `MdocEngine`），只需确认编译通过

## 8. cryo-em-server-backend: 硬删旧 mdoc 推进链路

- [ ] 8.1 删除 `com.cryo.task.tilt.MDocProcessor`
- [ ] 8.2 删除 `com.cryo.task.tilt.MDocContext`
- [ ] 8.3 删除 `com.cryo.task.tilt.MDocStep`
- [ ] 8.4 删除 `com.cryo.task.engine.flow.FlowManager.getMDocFlow(Task)` 方法（保留 `getMdocExportFlow`、`getMovieExportFlow` 等其它流）
- [ ] 8.5 删除 10 个 `Handler<MDocContext>` 实现类：`com.cryo.task.tilt.parse.MDocParseHandler`、`com.cryo.task.tilt.movie.MotionWait`、`com.cryo.task.tilt.movie.MovieConnect`、`com.cryo.task.tilt.stack.MdocStackHandler`、`com.cryo.task.tilt.filter.ExcludeHandler`、`com.cryo.task.tilt.align.CoarseAlign`、`com.cryo.task.tilt.patchtracking.PatchTracking`、`com.cryo.task.tilt.seriesalign.SeriesAlign`、`com.cryo.task.tilt.recon.AlignRecon`、`com.cryo.task.tilt.MdocSlurmStepHandler`
- [ ] 8.6 `com.cryo.task.engine.HandlerKey` 中删除仅被这些 handler 引用的枚举值：`MDocInit`、`MdodParser`、`MdocMotionWait`、`MovieConnect`、`MdocStack`、`MdocExclude`、`MdocCoarseAlign`、`MdocPatchTracking`、`MdocSeriesAlign`、`AlignRecon`、`MDOC_SLURM`；保留 `MDOC_EXPORT`（导出链路仍在）
- [ ] 8.7 清理 `com.cryo.task.tilt.export.MDocExportHandler` 中残留的 `import com.cryo.task.tilt.MDocContext;`
- [ ] 8.8 全仓 Grep `MDocContext`、`MDocProcessor`、`MDocStep`，确认全部 0 命中后跑 `mvn -pl cryo-web-server compile` 确认通过

## 9. 占位 BPMN 骨架

- [ ] 9.1 在 cryo-em-server-backend 的资源目录（与 `cryo-movie-minimal.bpmn` 同目录，如 `cryo-web-server/src/main/resources/assets/`）新增 `cryo-mdoc-minimal.bpmn`：`StartEvent → ServiceTask(id="mdoc-init-placeholder", name="Init Placeholder") → UserTask(id="mdoc-motion-wait", name="Wait Movie Motion Ready") → ServiceTask(id="mdoc-continue-placeholder", name="Continue Placeholder") → EndEvent`
- [ ] 9.2 确认 BPMN XML 合法（可用 Camunda Modeler 打开）；`<bpmn:process id="cryo-mdoc-minimal" isExecutable="true">`；UserTask 的 `id` 必须严格为 `mdoc-motion-wait`
- [ ] 9.3 在 README 或 design.md 已涉及位置补一句"占位 BPMN 不含真实业务，仅用于联调启动+UserTask+状态同步通路"

## 10. 联调验证（手动）

- [ ] 10.1 部署本 change 的 cryo-em-server-backend 与 kiwi-admin
- [ ] 10.2 将 `cryo-mdoc-minimal.bpmn` 导入 Kiwi-admin 并部署；记下 `BpmProcess.id`
- [ ] 10.3 在一个 `is_tomo=true` 的测试 Task 上设置 `mdocProcessDefinitionId = 该 BpmProcess.id`（若 `cryoems-task-workflow-selection` 已落地）或临时通过全局回退 `app.kiwi.workflow.movie-process-definition-id` 联调
- [ ] 10.4 启动 Task，确认：`MDocInstance.external_workflow_instance_id` 写入；Kiwi 实例视图能看到流程已在 `mdoc-motion-wait` UserTask；`MDocInstance.currentActivity` 同步显示
- [ ] 10.5 模拟（或等待真实）该 mdoc 对应的所有 movie 完成 motion；确认 `MdocKiwiWorkflowStateSyncListener` 调用 `completeUserTask`，Kiwi 实例继续到 placeholder ServiceTask 直至 End
- [ ] 10.6 实例终态后确认 `MDocInstance.current_step=FINISHED`、`process_status.processing=false`、`currentActivity=null`
- [ ] 10.7 异常场景验证：手动在 Kiwi 取消实例 → `MDocInstance.status` 显示 fail 与 cancel reason；删除实例 → `onInstanceNotFound` 触发 `restore`

## 11. 文档与归档

- [ ] 11.1 在 `cryo-em-server-backend` 仓 `application.yml` 注释段补充 mdoc 配置项的迁移说明（与 movie 段格式一致）
- [ ] 11.2 在本 change 的 design.md "Open Questions" 中确认 `mdoc-motion-wait` 命名最终采用版本（短 id），并把决议写回该处
- [ ] 11.3 全部任务勾选完毕后，按 `openspec-archive-change` skill 走归档流程
