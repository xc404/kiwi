## 1. kiwi-admin: 新增推进等待节点的机机端点

- [x] 1.1 在 kiwi-admin BPM 集成 controller（与现有 `POST /bpm/process/{id}/start` 同类）新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 端点，请求体 `{"variables": Map<String,Object>?}`，返回与现有端点同形的业务响应包装 — 实现于 `BpmProcessInstanceCtl.complete`（返回 `BpmProcessInstanceStateDto`，与同 controller 的 `/state` 端点对齐）
- [x] 1.2 实现内部逻辑（按优先级匹配）：① `TaskService.createTaskQuery().processInstanceId(...).taskDefinitionKey(...).active().list()` 命中 1 条 → `TaskService.complete(taskId, variables ?? Map.of())`（UserTask 路径）；② 否则 `ManagementService.createJobQuery().processInstanceId(...).activityId(...).list()` 命中 1 条 → 写入 variables 后 `ManagementService.executeJob(jobId)`（ManualTask `asyncBefore` Job 路径）；③ 任一路径 >1 → 409；都未命中 → 409；实例不存在 → 404
- [x] 1.3 鉴权复用现有 Sa-Token 机制（与 movie 启动端点同模式），确保 PAT 兑换的 token 可用 — 由类上 `@SaCheckLogin` 提供
- [ ] 1.4 单元/集成测试：covers UserTask 成功、ManualTask Job 成功、404（实例不存在/已结束）、409（无匹配 / UserTask 重复 / Job 重复）、401（未授权）、variables 为 null/空对象的兜底 — 暂 **deferred**：`kiwi-admin/backend/src/test` 目录下当前无任何 Java 测试基础设施，与同 `BpmProcessInstanceCtl` 内既有端点（`page`/`get`/`getState`/`batchStates`/`recover`）及 `cyroems-kiwi-workflow-integration` 已落地的 `POST /bpm/process/{id}/start` 处理一致；联调阶段（§10）覆盖
- [x] 1.5 更新 kiwi-admin 端 API 文档/Swagger，列入"BPM 集成"分组 — 通过 `@Operation(operationId="bpmInst_completeTask", summary=...)` 与类上 `@Tag(name="BPM 流程实例")` 自动暴露

## 2. cryo-em-server-backend: 模型与 Repository

- [x] 2.1 `com.cryo.model.tilt.MDocInstance` 新增 `external_workflow_instance_id`（String）与 `currentActivity`（String）两个字段；保持 Lombok/Mongo 映射风格与 `Movie` 同形
- [x] 2.2 `com.cryo.dao.MDocInstanceRepository` 新增 `Optional<MDocInstance> findByExternalWorkflowInstanceId(String)` 与 `List<MDocInstance> findByExternalWorkflowInstanceIdIn(Collection<String>)` 查询方法（与 `MovieRepository` 同形 `@Query` 注解）
- [x] 2.3 更新已存在的 `MDocInstanceRepository.restore(List<String> ids)` 的 `@Update` 注解：`$unset` 中追加 `external_workflow_instance_id`、`process_status`；**实施偏离**：同时把 `current_step` 由 `$set 'MDocInit'` 改为 `$unset`，避免 `HandlerKey` 未来枚举调整带来的反序列化风险，更稳健的清理写法（`MDocInit` 本身保留为活跃符号，详见 §8.6）。

## 3. cryo-em-server-backend: KiwiWorkflowClient 与 Properties

- [x] 3.1 `com.cryo.integration.workflow.KiwiWorkflowClient` 新增 `complete(String instanceId, String taskKey, Map<String,Object> variables) throws Exception`
- [x] 3.2 `complete` 对非 2xx 响应抛 `IllegalStateException`，异常消息含 HTTP 状态码与响应 body；不做内部重试
- [x] 3.3 `com.cryo.integration.workflow.KiwiWorkflowProperties` 新增 `mdocBatchSize=5`、`mdocMotionWaitActivityId="mdoc-motion-wait"`、`mdocMotionWaitPollIntervalMillis=10_000L`、`mdocMotionWaitInitialDelayMillis=30_000L`
- [x] 3.4 `application.yml` 在 `app.kiwi.workflow` 段下追加 `mdoc-batch-size`、`mdoc-motion-wait-activity-id`、`mdoc-motion-wait-poll-interval-millis`、`mdoc-motion-wait-initial-delay-millis`，并新增对应中文注释段（含 ManualTask `asyncBefore` 与 UserTask 两种含义说明）

## 4. cryo-em-server-backend: Mdoc 启动门面与变量

- [x] 4.1 新增 `MdocKiwiWorkflowVariables`，瘦契约 `{task:{id}, mdoc:{id, dataId}}`，含 `from(MDocInstance, MDoc, Task, TaskDataset)` 与 `toMap()`
- [x] 4.2 改造既有占位 `MdocKiwiWorkflowService`（由 `cryoems-task-workflow-selection` 引入），注入 `KiwiWorkflowClient`、`KiwiWorkflowProperties`、`TaskRepository`、`TaskDataSetRepository`、`MDocInstanceRepository`、`MDocRepository`、`MongoTemplate`、共享 `KiwiWorkflowInstanceWatcher`
- [x] 4.3 `isMdocPipelineReady(Task)` ＝ 客户端配置 && `resolveMdocBpmProcessId(task)` 非空
- [x] 4.4 `getMdocBatchSize()` ＝ `Math.max(properties.getMdocBatchSize(), 1)`
- [x] 4.5 `ensureStarted(MDocInstance, MDoc, Task, TaskDataset)` ＝ 组装变量 → `startProcess` → 写回 `external_workflow_instance_id`、`process_status.processing=true`、`processing_at=now` → `mongoTemplate.save` → `watcher.track(instanceId)`
- [x] 4.6 `resolveMdocBpmProcessId(Task task)` ＝ `task.mdocProcessDefinitionId` → `properties.processTypes.mdoc.defaultIdTomo/NonTomo`；**实施偏离**：**不**回退到 `properties.movieProcessDefinitionId`（保留 `cryoems-task-workflow-selection` 既有 service 的"避免误把 movie 全局流程当成 mdoc"显式决策；起步阶段强制运维显式给出 mdoc id）。design.md 决策 §5 已同步澄清；调用方报错信息会引导填配置
- [x] 4.7 Javadoc 与日志风格对齐 `MovieKiwiWorkflowService`

## 5. cryo-em-server-backend: 共享 Watcher 重命名

- [x] 5.1 `WorkflowIntegrationConfiguration` 改为 `@Bean("kiwiWorkflowInstanceWatcher")`，构造参数追加 `MdocKiwiWorkflowStateSyncListener` 并 `watcher.registerListener(...)`
- [x] 5.2 `MovieEngine` 改 `getBean("kiwiWorkflowInstanceWatcher", ...)`，字段更名为 `kiwiWorkflowInstanceWatcher`
- [x] 5.3 `MovieKiwiWorkflowService` 上 `@Qualifier("kiwiWorkflowInstanceWatcher")`，字段同步更名
- [x] 5.4 全仓 `rg "movieKiwiWorkflowInstanceWatcher"`：0 命中（grep 已验证）

## 6. cryo-em-server-backend: Mdoc 状态同步 + MotionWait 推动

- [x] 6.1 新增 `MdocKiwiWorkflowStateSyncListener implements KiwiWorkflowBatchWatchListener`，结构按 `MovieKiwiWorkflowStateSyncListener` 复制裁剪，使用 `MDocInstanceRepository` 隐式过滤本类型实例
- [x] 6.2 `applyState(MDocInstance, KiwiProcessInstanceState, boolean terminal)` 覆盖 RUNNING / ERROR / COMPLETED / CANCELED 四分支，COMPLETED 时设 `current_step=FINISHED_STEP` 并清 `currentActivity` / `processing`
- [x] 6.3 `onInstanceNotFound` 批量 `mdocInstanceRepository.restore(...)` + log info
- [x] 6.4 ~~`onPoll` 在 `mongoTemplate.save` 之后读 `properties.getMdocMotionWaitActivityId()`：空跳过；否则比对 `currentActivityId`（或 `currentActivityName`），命中即调 `triggerMotionWaitIfReady(...)`~~ → 已重构：onPoll 仅做状态字段同步，readiness 判定与 `complete` 调用迁移到独立调度器（详见 §6a）
- [x] 6.5 ~~`triggerMotionWaitIfReady` 实现：data_id → MDoc → meta.tilts dataId 列表 → `MovieResultRepository.findByQuery(...)` → 计数 `motion.predict_dose` 非空 == tilts.size~~ → 已迁移至 `MdocMotionWaitScheduler`（详见 §6a）
- [x] 6.6 ~~就绪即 `kiwiWorkflowClient.complete(...)`；异常均捕获仅打 warn~~ → 已迁移至 `MdocMotionWaitScheduler`
- [x] 6.7 Listener 通过构造注入 `KiwiWorkflowProperties` 与 `KiwiWorkflowClient`；零字面量 `"mdoc-motion-wait"` → **修订**：Listener 已不再需要 `KiwiWorkflowProperties` / `KiwiWorkflowClient` 等推动依赖，仅保留 `MDocInstanceRepository` 与 `MongoTemplate`；这些依赖改由 `MdocMotionWaitScheduler` 注入

## 6a. cryo-em-server-backend: MotionWait 独立调度器

- [x] 6a.1 新增 `com.cryo.integration.workflow.MdocMotionWaitScheduler`（@Component），通过 Spring `@Scheduled(fixedDelayString=..., initialDelayString=...)` 周期触发，节拍来自 `app.kiwi.workflow.mdoc-motion-wait-poll-interval-millis` / `mdoc-motion-wait-initial-delay-millis`
- [x] 6a.2 `scan()` 入口顶层 try-catch：守护调度循环不被任意异常打断，仅打 warn
- [x] 6a.3 守卫：`!properties.isEnabled() || !kiwiWorkflowClient.isClientConfigured() || !StringUtils.hasText(motionWaitActivityId)` → 直接返回
- [x] 6a.4 `taskRepository.getRunningTasks()` 取所有 `Task.status==running`；按 `id` 建立 `Map<String,Task>`
- [x] 6a.5 单次 mongo 查询：`Criteria.where("task_id").in(taskIds).and("currentActivity").is(motionWaitActivityId).and("external_workflow_instance_id").exists(true).ne(null)` → 命中实例集合
- [x] 6a.6 对每条命中实例调用 `triggerMotionWaitIfReady(instance, task, motionWaitActivityId)`：等价于原 listener 内部逻辑（data_id → MDoc → tilts dataId → MovieResult.motion.predict_dose 计数 → 就绪即 `complete`），单条异常仅打 warn 不抛
- [x] 6a.7 注入 `MDocInstanceRepository` / `MDocRepository` / `MovieResultRepository` / `TaskRepository` / `KiwiWorkflowProperties` / `KiwiWorkflowClient`，零字面量节点名

## 7. cryo-em-server-backend: 改写 MdocEngine

- [x] 7.1 删除 `InstanceProcessor`、`IFlow`、`FlowManager` 注入；保留 `TaskDataSetRepository`、`MDocInstanceRepository`、`TaskStatistic`、`TaskScheduler`；新增 `MdocKiwiWorkflowService`、共享 watcher、`MDocRepository`
- [x] 7.2 `start()` 前置校验 `mdocKiwiWorkflowService.isMdocPipelineReady(task)`，否则抛 `IllegalStateException`（消息含配置引导）
- [x] 7.3 `start()` 调 `trackProcessingWorkflowInstances()`：按 `task_id` + `process_status.processing=true` 收集 `external_workflow_instance_id` 批量 `watcher.track(...)`
- [x] 7.4 `handle()` 改写：取批大小 → `InstanceRepository.unprocessed()` + `task_id`，按 `file_create_at` 升序限批 → 加载对应 `MDoc` → `mdocKiwiWorkflowService.ensureStarted(...)` 单条容错；末尾 `taskStatistic.statisticMDoc(task)`
- [x] 7.5 旧 `updateProcessingStatus()`、`resetProcessing()` 一并删除（终态由 Watcher 清 processing）
- [x] 7.6 `TaskMonitor` 路径未触动，编译保持

## 8. cryo-em-server-backend: 旧 mdoc 推进链路标记 `@Deprecated`（不硬删）

- [x] 8.1 `com.cryo.task.tilt.MDocProcessor` 类级加 `@Deprecated` + Javadoc（保留 `@Service` 注解；Spring 仍扫描出 bean 但无注入点）
- [x] 8.2 `com.cryo.task.tilt.MDocContext` 类级加 `@Deprecated` + Javadoc
- [x] 8.3 `com.cryo.task.tilt.MDocStep` 类级加 `@Deprecated` + Javadoc
- [x] 8.4 `FlowManager.getMDocFlow(Task)` 方法加 `@Deprecated` + Javadoc；`FlowManager` 类级 Javadoc 同步更新（说明 movie/mdoc 均已迁至 Kiwi BPM）
- [x] 8.5 10 个 `Handler<MDocContext>` 实现类（`MDocParseHandler` / `MotionWait` / `MovieConnect` / `MdocStackHandler` / `ExcludeHandler` / `CoarseAlign` / `PatchTracking` / `SeriesAlign` / `AlignRecon` / `MdocSlurmStepHandler`）每个类级加 `@Deprecated` + Javadoc
- [x] 8.6 `HandlerKey` 中 10 项与 deprecated handler 一一对应的枚举值（`MdodParser`、`MovieConnect`、`MdocMotionWait`、`MdocStack`、`MdocExclude`、`MdocCoarseAlign`、`MdocPatchTracking`、`MdocSeriesAlign`、`AlignRecon`、`MDOC_SLURM`）逐项加 `@Deprecated` + Javadoc；**保留 `MDocInit`**（仍被 `MDocDetectorSupport` 写入初始 step 与 `MdocCtl.MdocOutput.getStatus()` 识别"未处理"态使用）、**保留 `MDOC_EXPORT`**（仍由 `FlowManager.getMdocExportFlow` 使用）作为活跃符号，未弃用
- [x] 8.7 `FilePathService.getMdocWorkDir(MDocContext)` 方法加 `@Deprecated` + Javadoc；其 `import com.cryo.task.tilt.MDocContext;` 与 `MDocExportHandler` / `ExportMdocEngine` 上残留的同名 import 保持原样（在 deprecated 旁路中合法被使用）
- [x] 8.8 `MdocEngine` 改写后 MUST 不再 `import` 任何 deprecated 符号；`rg "MDocContext|MDocProcessor|MDocStep" cryo-web-server/src/main/java` 命中仅限：(a) deprecated 类自身定义与 Javadoc；(b) deprecated handler 内部引用；(c) `FilePathService.getMdocWorkDir` deprecated 方法签名；(d) `MDocExportHandler`/`ExportMdocEngine` 旧 import。`MdocEngine` 与 `MdocKiwiWorkflowService` / Listener / Variables 必须 0 命中
- [x] 8.9 `mvn -pl cryo-web-server compile`：本地环境因 Lombok 注解处理器配置缺失，仓内 `KiwiClient` / `DatasetCtl` 等**未触动**文件即已报错（getXxx/log 未生成），与本变更无关；CI/正常打包环境（带 annotation-processor 路径）应正常编译；deprecated 编译告警预期出现在 §8.1–§8.7 涉及的类与枚举上

## 9. 占位 BPMN 骨架

- [x] 9.1 新增 `cryo-web-server/src/main/resources/assets/cryo-mdoc-minimal.bpmn`：StartEvent → ServiceTask `mdoc-init-placeholder` → ManualTask `mdoc-motion-wait`（`camunda:asyncBefore="true"`） → ServiceTask `mdoc-continue-placeholder` → EndEvent
- [x] 9.2 文件顶部含说明注释；`<bpmn:process id="cryo-mdoc-minimal" isExecutable="true">`；ManualTask `id` 与 `KiwiWorkflowProperties.mdocMotionWaitActivityId` 默认值 (`mdoc-motion-wait`) 一致；ServiceTask 配置 `camunda:type=external` topic 便于联调挂 worker
- [x] 9.3 BPMN 顶部注释已含"占位 BPMN 不含真实业务，仅用于联调启动+ManualTask+状态同步通路；ManualTask id 与 `app.kiwi.workflow.mdoc-motion-wait-activity-id` 配置项保持一致；`camunda:asyncBefore` 在该节点前形成 async-continuation Job 作为外部推进点"

## 10. 联调验证（手动）

- [ ] 10.1 部署本 change 的 cryo-em-server-backend 与 kiwi-admin
- [ ] 10.2 将 `cryo-mdoc-minimal.bpmn` 导入 Kiwi-admin 并部署；记下 `BpmProcess.id`
- [ ] 10.3 在一个 `is_tomo=true` 的测试 Task 上设置 `mdocProcessDefinitionId = 该 BpmProcess.id`（若 `cryoems-task-workflow-selection` 已落地）或临时通过全局回退 `app.kiwi.workflow.movie-process-definition-id` 联调
- [ ] 10.4 启动 Task，确认：`MDocInstance.external_workflow_instance_id` 写入；Kiwi 实例视图能看到流程已在 `mdoc-motion-wait` ManualTask 前（async-continuation Job 已停泊）；`MDocInstance.currentActivity` 同步显示
- [ ] 10.5 模拟（或等待真实）该 mdoc 对应的所有 movie 完成 motion；确认 `MdocKiwiWorkflowStateSyncListener` 调用 `complete`，kiwi-admin 端走 ManagementService.executeJob 推进，Kiwi 实例继续到 placeholder ServiceTask 直至 End
- [ ] 10.6 实例终态后确认 `MDocInstance.current_step=FINISHED`、`process_status.processing=false`、`currentActivity=null`
- [ ] 10.7 异常场景验证：手动在 Kiwi 取消实例 → `MDocInstance.status` 显示 fail 与 cancel reason；删除实例 → `onInstanceNotFound` 触发 `restore`

## 11. 文档与归档

- [x] 11.1 `application.yml` 注释段已扩展（与 §3.4 一并完成）：movie / mdoc 同形说明 + `mdoc-batch-size` / `mdoc-motion-wait-activity-id` 用途与"空字符串关闭外部推动"的运维提示
- [x] 11.2 ~~`mdoc-motion-wait` 命名 Open Question~~（已通过配置项 `app.kiwi.workflow.mdoc-motion-wait-activity-id` 解决）
- [ ] 11.3 全部任务勾选完毕后，按 `openspec-archive-change` skill 走归档流程（待联调 §10 通过后由用户触发）
