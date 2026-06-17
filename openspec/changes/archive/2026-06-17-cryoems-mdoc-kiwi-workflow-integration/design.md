## Context

### 当前 mdoc 链路（本 change 标记 `@Deprecated`，保留代码不再调度）

```
TaskMonitor
  └── MdocEngine.handle()   每 10s
        ├── instanceProcessor.getIdleCount()
        ├── taskDataSetRepository.findById(...)
        ├── mDocInstanceRepository → 取一批未处理 MDocInstance
        ├── mDocRepository → 加载对应 MDoc
        ├── new MDocContext(applicationContext, taskDataset, flow, task, instance, mDoc)
        └── instanceProcessor.submit(ctx)   → InstanceProcessor 在线程池里
                                              按 IFlow 顺序跑：
              MDocInit → MdodParser → MovieConnect → MdocMotionWait →
              MdocStack → MdocCoarseAlign → MdocPatchTracking →
              MdocSeriesAlign → AlignRecon → MDOC_SLURM → FINISHED
```

### 已就绪的 movie 模式（被本 change 复用）

```
MovieEngine (Kiwi)                Kiwi-admin (Camunda)
  ensureStarted(movie,task,ds) ─▶ POST /bpm/process/{bpmId}/start
  external_workflow_instance_id ◀─ instanceId
       │
       ▼
movieKiwiWorkflowInstanceWatcher（轮询 Kiwi instance 状态）
  ├─ MovieKiwiWorkflowStateSyncListener (Movie 同步)
       onPoll / onInstanceTerminal / onInstanceNotFound
```

### 约束

- **不再在 cryoEMS 本进程内推进 mdoc 步骤**：业务步骤改由 BPMN 内 ServiceTask 推进，本次 change 仅实现"启动+状态同步+ManualTask 推动"的运行时机制，BPMN 业务步骤的 JavaDelegate 留给后续 `cryoems-bpm-mdoc-javadelegate-migration` change。
- **不引入新的"按类型路由" watcher 层**：共享 `KiwiWorkflowInstanceWatcher` + 多个 listener，listener 内部通过自身 repository 隐式过滤。
- **mdoc 的 MotionWait 是跨实例 join**：cryoEMS 已存的 `task/tilt/movie/MotionWait` 步骤本质是"等同一批 movie 的 `MovieResult.motion` 完成"，与 movie 处理流的"单实例独立"语义不同。本次以 ManualTask（`camunda:asyncBefore="true"`，未来也兼容 UserTask）把"等待"声明在 BPMN 上，由 cryoEMS 在 listener 里轮询条件并主动 `complete` 推动。

## Goals / Non-Goals

**Goals:**

1. mdoc 流水线的本地推进链路（`MDocProcessor` / `MDocContext` / `MDocStep` / `IFlow` + 10 个 `Handler<MDocContext>`）**统一加 `@Deprecated`**：保留代码、`MdocEngine` 改写后不再调度（详见 §决策 4）。
2. `MdocEngine` 改写为"按批 `ensureStarted` → 不再本地跑"，与 `MovieEngine` 同形。
3. 共享 watcher：`WorkflowIntegrationConfiguration` 中 bean 改名为 `kiwiWorkflowInstanceWatcher`，并注册 movie/mdoc 两个 listener，`MovieEngine` 中所有 `movieKiwiWorkflowInstanceWatcher` 引用同步更新。
4. `MdocKiwiWorkflowStateSyncListener` 在 `onPoll` 内识别 `currentActivityId == properties.mdocMotionWaitActivityId` 的实例，扫描 `MDoc.meta.tilts[].dataId → MovieResult.motion` 完成情况；齐则调 `KiwiWorkflowClient.complete(instanceId, properties.mdocMotionWaitActivityId, vars)`。
5. kiwi-admin 新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 机机端点，由 Sa-Token 鉴权（与现有 movie 启动端点同模式），同时支持 UserTask（`TaskService.complete`）与 ManualTask `asyncBefore` Job（`ManagementService.executeJob`）两种推进语义。
6. 提供联调可用的占位 BPMN `cryo-mdoc-minimal.bpmn`：`Start → ServiceTask:placeholder(Init) → ManualTask(id 由 app.kiwi.workflow.mdoc-motion-wait-activity-id 配置，默认 mdoc-motion-wait，camunda:asyncBefore="true") → ServiceTask:placeholder(Continue) → End`。
7. 不破坏 movie 链路的现有运行时行为（仅 bean 名替换）。

**Non-Goals:**

- 不在 cryoems-bpm 中实现 mdoc 业务 `JavaDelegate`（属于后续 change）。
- 不实现"BPMN 内回查 cryoEMS Mongo 拿 MDoc/MDocMeta"的具体机制（瘦契约只携带 `task.id` + `mdoc.id`，业务 delegate 自行处理）。
- 不引入 BPMN message/signal 事件代替 ManualTask/UserTask 握手（已选 ManualTask asyncBefore + 轮询 complete 方案，endpoint 同时兼容 UserTask）。
- 不修改 mdoc 导出链路（`ExportMdocEngine`/`MDocExportContext`/`MDocExportHandler`/`HandlerKey.MDOC_EXPORT`）。
- ~~不实现 `MdocMotionReadinessPoller` 独立 scheduler（决策选了"挂在 listener.onPoll 里"复用 watcher 节拍）~~ → **已修订**：readiness 判定与 `complete` 调用迁移到独立 `MdocMotionWaitScheduler`，按 `Task.status=running` + `MDocInstance.currentActivity == motionWaitActivityId` 直接 mongo 查询触发，与 watcher / listener 解耦（详见决策 2 修订条）。
- 不做 deprecated 旧链路的硬删除（留给下一 change），本次仅 `@Deprecated` 标注 + 取消调度。

## Decisions

### 决策 1：共享 watcher，bean 改名 `kiwiWorkflowInstanceWatcher`

**Why**：`KiwiWorkflowInstanceWatcher` 已支持 `registerListener` 多个；新增 mdoc listener 自然走该接口。bean 名携带 `movie` 前缀对 mdoc 用例产生语义误导，且未来如新增 export 等场景仍需共享。

**Alternatives considered**：

- 保留旧 bean 名作为 `@Primary` 兼容、再加新名：留两个名字增加迷惑度，无收益（仓库内唯一使用方就是 `MovieEngine`，一次性替换成本低）。
- 为 mdoc 新建独立 watcher bean：HTTP 请求数翻倍，且与"共享"的产品方向相反。

**Listener 归属过滤**：

```
onPoll(snapshot):
  按 movieRepo / mdocInstanceRepo 自行查 instanceId → 不属于自己的跳过

onInstanceTerminal:
  同上，按 repo 查；找不到不报错

onInstanceNotFound:
  同上，按 repo 查；空 list 直接返回
```

无需"按类型路由"中间层。

### 决策 2：MotionWait 用 ManualTask（`asyncBefore="true"`，`activityId` 可配置，默认 `mdoc-motion-wait`） + cryoEMS 轮询 complete

**Why**：

- BPMN 上把"等待前置 movie 完成"显式声明为节点，可视化清晰、Camunda 历史可追踪。
- 选 **ManualTask + `camunda:asyncBefore="true"`**：Camunda 会在进入节点前生成一条 async-continuation Job 作为外部推进点；kiwi-admin endpoint 通过 `ManagementService.executeJob` 推动。语义上"人工/外部系统手工推动"更贴近本场景，且不在 Camunda Task 列表中堆积 UserTask（避免与真正的人工任务混淆）。
- endpoint 同时兼容 UserTask 用法（先 `TaskService.complete`，失败再查 Job 推进），运维侧可按需切换 BPMN 元素类型而无需改 cryoEMS。
- 与本次"瘦契约 + 业务后续在 BPMN 里实现"的方向一致。
- `activityId` 走配置：避免硬编码在 listener 里；不同环境/不同 BPMN 版本可以独立配置节点名，cryoEMS 不需要发版。

**Alternatives considered**：

- 纯 UserTask：Camunda Task 列表会堆积"系统任务"，与真正的人工任务语义混淆；不易做 UI 过滤。仍作为 endpoint 的兼容路径保留。
- BPMN 内 Timer + ServiceTask 自轮询条件：需要在 BPMN 内具备"回查 cryoEMS"的能力（本次不实现 delegate），且 timer + condition 难以表达"齐了才走"。
- Message/Signal 事件由 movie 流程发给 mdoc：movie 流程不知道 mdoc 的 instanceId；需 cryoEMS 路由，反而复杂。
- cryoEMS 侧做"前置 readiness gate"，齐了才启动 mdoc 流程：把等待从 BPMN 中剔除，可读性下降；且若后续某些 mdoc 步骤在等待中也想消费 movie 结果，必须重新在 BPMN 表达。

**轮询归属（修订）**：迁移到独立调度器 `MdocMotionWaitScheduler`（@Component + @Scheduled），与 `KiwiWorkflowInstanceWatcher` / Listener 解耦；listener 仅负责把 `KiwiProcessInstanceState` 写回 `MDocInstance.currentActivity / status / processing`，scheduler 仅负责判定就绪并 `complete`。

```
scheduler.scan()  @Scheduled fixedDelay = 10s（可配）
  1. 守卫：properties.enabled && client.isConfigured() && motionWaitActivityId 非空
  2. taskRepository.getRunningTasks()  →  Map<id,Task>
  3. mongoQuery:
       Criteria.where("task_id").in(runningTaskIds)
              .and("currentActivity").is(motionWaitActivityId)
              .and("external_workflow_instance_id").exists(true).ne(null)
  4. 命中实例 → 逐条 triggerMotionWaitIfReady(instance, task, motionWaitActivityId)：
       a. 取该 MDocInstance.data_id → 查 MDoc.meta.tilts[].dataId
       b. 按 dataId 批量查 MovieResult.motion 完成情况
       c. 全齐 → kiwiWorkflowClient.complete(
              instanceId, motionWaitActivityId,
              vars={mdoc_motion_ready=true, mdoc_tilt_count=N})
       d. 不齐 → 下一轮再判
  scan() 入口顶层 try-catch；单条异常仅打 warn 不抛
```

**为何独立 scheduler（替代"挂 onPoll"）**：

- watcher.onPoll 节拍由 Kiwi instance 状态查询驱动，readiness 判定本质是 cryoEMS 本地 Mongo 查询，无需依赖 Kiwi 状态 snapshot 的刷新时机。
- 解耦后 listener / watcher 的请求耗时不再受 Mongo 二次查询/HTTP `complete` 调用拖累。
- 以"task=running + currentActivity 命中"为入口可一次 mongo 查询过滤所有候选实例（含 watcher snapshot 周期外、刚被 listener 回写但未到下一轮 onPoll 的实例），覆盖更全。
- 节拍可独立调（默认 10s，可拉长/缩短），不影响状态同步轮询。

**配置位**：

- `app.kiwi.workflow.mdoc-motion-wait-activity-id`（默认 `mdoc-motion-wait`）：BPMN 节点 id；空字符串/null 关闭外部推动。
- `app.kiwi.workflow.mdoc-motion-wait-poll-interval-millis`（默认 10_000）：scheduler 固定扫描间隔。
- `app.kiwi.workflow.mdoc-motion-wait-initial-delay-millis`（默认 30_000）：scheduler 启动后首次扫描延迟，避免在依赖 bean 尚未完全装配时触发 Kiwi 调用。

**风险**：

| Risk | Mitigation |
|------|-----------|
| `complete` 同步调用慢 | scheduler 是独立线程池任务，慢调用不阻塞 watcher onPoll；批内逐条 try-catch，单条慢/失败不影响其他 |
| 同一实例多次 complete（scheduler 与 listener 写回 currentActivity 之间存在时序窗口） | kiwi-admin 端 complete 必须幂等地处理"task/job 已完成"——返回稳定 4xx 而非 5xx；scheduler 看到异常时记 warn 不重试，下一轮 listener 写回 currentActivity=null（COMPLETED 后）即不会再命中 |
| ManualTask `asyncBefore` Job 失败导致 retries=0 死锁 | 占位 BPMN 内 ManualTask 不挂任何 ExecutionListener/JavaDelegate，executeJob 不会业务失败；后续如挂 listener 需评估错误处理 |
| readiness 判断与 movie 流程的 `MovieResult.motion` 写入存在时序裂缝 | 仅触发 complete，不删数据；下一轮若 movie 又写新结果不影响 |
| `currentActivity` 字段语义（name vs id）不一致导致漏命中 | scheduler 直接按 mongo 已存的 `currentActivity` 字段等值比对（与 listener `resolveCurrentActivity` 落库保持一致），运维侧只需把 `mdoc-motion-wait-activity-id` 配置为 BPMN 节点 id（默认无 `name` 时）即可命中；如 BPMN 后续给该节点加了 `name`，把配置改成对应 name |

### 决策 3：kiwi-admin 端 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`

**契约**：

```
POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete
Headers: Authorization: Bearer <Sa-Token from PAT>
Body: { "variables": { ... } }   // 可空对象

行为（按优先级匹配）：
  1) TaskService.createTaskQuery().taskDefinitionKey(taskKey).active() 命中 1 条
     → TaskService.complete(taskId, variables)
  2) 否则 ManagementService.createJobQuery().activityId(taskKey) 命中 1 条
     → 写入 variables 到该 execution，再 ManagementService.executeJob(jobId)
     （覆盖 ManualTask camunda:asyncBefore="true" 形成的 async-continuation Job 等场景）
  3) 都没命中或多条匹配 → 错误

Responses:
  200 OK     { ... 业务包装 }    — 推进成功（UserTask 完成或 Job 执行）
  404 NOT_FOUND  — instance 不存在（已结束/已清理）
  409 CONFLICT   — 无 active UserTask 也无 async-continuation Job 与 taskKey 匹配；
                   或同 taskKey 命中多条 UserTask/Job
  其他           — 与现有 BPM 机机端点一致的错误体
```

**Why 用 path 上的 `taskKey` 而非 client 先查再传 `taskId`**：

- cryoEMS 不需要持久化 Camunda 内部 taskId / jobId；约定值 `mdoc-motion-wait` 跨实例稳定。
- 减少一次往返。
- Camunda `TaskService.createTaskQuery().taskDefinitionKey(...)` 与 `ManagementService.createJobQuery().activityId(...)` 都可直接按 BPMN 元素 id 定位。

**鉴权与现有端点一致**：复用 PAT → Sa-Token 兑换；同一 `BpmMachineIntegrationCtl`（或与之等价）下挂载。

### 决策 4：`MDocContext` / `MDocProcessor` / `MDocStep` 与全部 `Handler<MDocContext>` 标记 `@Deprecated`（不硬删）

**Why**：

- 这些类持有的"业务知识"在 BPMN delegate 实现期需要参考；先 `@Deprecated` 保留一版可让 BPMN delegate 作者直接对照旧代码。
- 仅靠 git history 找历史实现成本较高（跨文件、跨方法），且在 review 期间不方便。
- `MdocEngine` 改造后已不再注入 `MDocProcessor` / `FlowManager.getMDocFlow`，旧链路实质失活，保留代码不会被运行时调度。
- 下一次 change（如 `cryoems-bpm-mdoc-javadelegate-migration` 完成时）再统一硬删。

**Trade-off**：

- 不利用编译失败来暴露遗漏引用点；改为依赖 `@Deprecated` 编译器告警 + 显式 `Grep` 检查 `MDocContext` / `MDocProcessor` 在 `MdocEngine` 外的引用。
- 在 BPMN delegate 实现前，**mdoc 跑不出真实结果**（仅启动 + 卡在 ManualTask 后又被 complete → 走到 ServiceTask placeholder → 结束）。这是预期效果，与 movie 当时的过渡状态一致。
- 若线上仍依赖旧 mdoc 链路出结果，本 change 应与 `cryoems-bpm-mdoc-javadelegate-migration` 一起发布；本 change 单独发布将令 mdoc 在过渡期无业务输出。

**Deprecation 覆盖清单**：

```
class:
  - com.cryo.task.tilt.MDocContext
  - com.cryo.task.tilt.MDocProcessor              (@Service 仍可扫描，但无注入点)
  - com.cryo.task.tilt.MDocStep
  - com.cryo.task.tilt.parse.MDocParseHandler
  - com.cryo.task.tilt.movie.MotionWait
  - com.cryo.task.tilt.movie.MovieConnect
  - com.cryo.task.tilt.stack.MdocStackHandler
  - com.cryo.task.tilt.filter.ExcludeHandler
  - com.cryo.task.tilt.align.CoarseAlign
  - com.cryo.task.tilt.patchtracking.PatchTracking
  - com.cryo.task.tilt.seriesalign.SeriesAlign
  - com.cryo.task.tilt.recon.AlignRecon
  - com.cryo.task.tilt.MdocSlurmStepHandler

method:
  - com.cryo.task.engine.flow.FlowManager#getMDocFlow(Task)
  - com.cryo.service.FilePathService#getMdocWorkDir(MDocContext)

enum constants in com.cryo.task.engine.HandlerKey:
  MdodParser, MovieConnect, MdocMotionWait, MdocStack, MdocExclude,
  MdocCoarseAlign, MdocPatchTracking, MdocSeriesAlign, AlignRecon, MDOC_SLURM
（MDocInit 仍作为 MDocInstance.current_step 状态标记，未弃用；
  MDOC_EXPORT 仍由 FlowManager#getMdocExportFlow 使用，未弃用。）
```

### 决策 5：瘦契约 `{ task: { id }, mdoc: { id } }`

**Why**：BPMN delegate 后续实现时直连 cryoEMS Mongo（与 `cryoems-bpm-movie-javadelegate-migration` 中 movie delegate 的方式一致），不需要将 `MDocMeta` 整体塞进流程变量。

**变量结构**：

```json
{
  "task": { "id": "<Task._id>" },
  "mdoc": { "id": "<MDocInstance._id>", "dataId": "<MDocInstance.data_id>" }
}
```

`mdoc.dataId` 用于 BPMN delegate 直接定位到 `MDoc` 文档（`MDoc._id == MDocInstance.data_id`），避免再查一跳。

### 决策 6：`MDocInstance` 字段对齐 `Movie`

```java
public class MDocInstance extends Instance {
    private String external_workflow_instance_id;
    private String currentActivity;
}
```

`MDocInstanceRepository` 新增：

```java
Optional<MDocInstance> findByExternalWorkflowInstanceId(String instanceId);
List<MDocInstance> findByExternalWorkflowInstanceIdIn(Collection<String> ids);
```

`restore(List<String> ids)` 已存在，扩展为同时 `$unset: external_workflow_instance_id`，与 `MovieRepository.restore` 一致。

### 决策 7：流程 id 解析三段回退

```
MdocKiwiWorkflowService.resolveMdocBpmProcessId(task):
  1. task.mdocProcessDefinitionId     (cryoems-task-workflow-selection 引入)
  2. processTypes.mdoc.defaultIdTomo / defaultIdNonTomo
                                       (cryoems-task-workflow-selection 引入)
  3. properties.movieProcessDefinitionId  仅迁移期回退；如未配置则返回 null
```

若 `cryoems-task-workflow-selection` 尚未落地：仅 (3) 可用，本 change 仍能跑通"启动+状态同步"（前提：所有 task 复用同一全局回退）。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| 本 change 单独发布 → mdoc 无业务输出 | 与 `cryoems-bpm-mdoc-javadelegate-migration` 一起发布；或在 BPMN 占位骨架后续逐步替换 ServiceTask placeholder |
| bean 改名 `kiwiWorkflowInstanceWatcher` 漏改 | 改完后 `Grep` `movieKiwiWorkflowInstanceWatcher`，预期 0 命中；Spring 启动期注入失败即可暴露 |
| `complete` 在 mdoc 实例还未到达 MotionWait 时被触发（如刚启动） | listener 检查 `currentActivityId == properties.mdocMotionWaitActivityId` 之后才触发；snapshot 是 Kiwi 真实状态，避免误触 |
| `MovieResult.motion` 查询性能（大批量 tilts） | 与既有 `task/tilt/movie/MotionWait.handle` 等价的 `findByQuery(...in(ids))`，已是单次 in 查询；mdoc 数量级远小于 movie，无新增风险 |
| deprecated 旧链路被新代码意外引用 | `@Deprecated` 触发编译告警；review 时 `Grep MDocContext\\|MDocProcessor` 在 `task.tilt` 包外应 0 命中（仅 `MdocEngine` 外、且不在 deprecated 链路内） |
| 跨仓 PR 节奏（cryo-em-server-backend 与 kiwi 仓） | `tasks.md` 按仓分组；联调前需要先合 kiwi 仓的 `complete` 端点 |

## Migration Plan

```
T0  归档/合并 kiwi-admin 端 POST .../tasks/{taskKey}/complete 端点
T1  归档/合并 cryo-em-server-backend：MDocInstance 字段 + Repository + Properties + KiwiWorkflowClient.complete
T2  归档/合并 cryo-em-server-backend：MdocKiwiWorkflowService / Variables / StateSyncListener
T3  归档/合并 cryo-em-server-backend：WorkflowIntegrationConfiguration bean 改名 + MovieEngine 同步替换
T4  归档/合并 cryo-em-server-backend：MdocEngine 改写 + 旧 MDocContext 一坨标 @Deprecated（不删）
T5  部署 cryo-mdoc-minimal.bpmn（ManualTask 占位）到 Kiwi-admin；在 Task 上填 mdocProcessDefinitionId
T6  联调：tomo 数据集任务启动 → 观察 mdoc 实例启动 → ManualTask 卡点 → 自动 complete → 终态
T7  下一 change：在 BPMN delegate 完成后硬删 deprecated 代码
```

**回滚**：本 change 不再做 BREAKING 硬删，旧代码以 `@Deprecated` 保留；如需运行时回退至旧 mdoc 链路，仍需 revert `MdocEngine` 改写与 watcher bean 改名 commits（这两者构成事实上的破坏点）。BPMN 占位文件可保留，不影响旧版。

## Open Questions

- `KiwiWorkflowClient.complete` 的 `variables` 入参是否必须？
  - 第一版传空 Map；保留参数以便后续 BPMN 节点在 complete 时接收完成原因等信息。
- ~~BPMN MotionWait 节点的 `activityId` 命名空间~~（已解决：通过 `app.kiwi.workflow.mdoc-motion-wait-activity-id` 配置项控制；默认 `mdoc-motion-wait`）。
- ~~MotionWait 节点用 UserTask 还是 ManualTask~~（已解决：ManualTask + `camunda:asyncBefore="true"`；endpoint 同时兼容 UserTask 路径）。
