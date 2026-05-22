## Context

### 当前 mdoc 链路（hard-deleted by this change）

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

- **不再在 cryoEMS 本进程内推进 mdoc 步骤**：业务步骤改由 BPMN 内 ServiceTask 推进，本次 change 仅实现"启动+状态同步+UserTask 推动"的运行时机制，BPMN 业务步骤的 JavaDelegate 留给后续 `cryoems-bpm-mdoc-javadelegate-migration` change。
- **不引入新的"按类型路由" watcher 层**：共享 `KiwiWorkflowInstanceWatcher` + 多个 listener，listener 内部通过自身 repository 隐式过滤。
- **mdoc 的 MotionWait 是跨实例 join**：cryoEMS 已存的 `task/tilt/movie/MotionWait` 步骤本质是"等同一批 movie 的 `MovieResult.motion` 完成"，与 movie 处理流的"单实例独立"语义不同。本次以 UserTask 把"等待"声明在 BPMN 上，由 cryoEMS 在 listener 里轮询条件并主动 `completeUserTask`。

## Goals / Non-Goals

**Goals:**

1. mdoc 流水线的本地推进链路（`MDocProcessor` / `MDocContext` / `MDocStep` / `IFlow` + 10 个 `Handler<MDocContext>`）**硬删**。
2. `MdocEngine` 改写为"按批 `ensureStarted` → 不再本地跑"，与 `MovieEngine` 同形。
3. 共享 watcher：`WorkflowIntegrationConfiguration` 中 bean 改名为 `kiwiWorkflowInstanceWatcher`，并注册 movie/mdoc 两个 listener，`MovieEngine` 中所有 `movieKiwiWorkflowInstanceWatcher` 引用同步更新。
4. `MdocKiwiWorkflowStateSyncListener` 在 `onPoll` 内识别 `currentActivityId == "mdoc-motion-wait"` 的实例，扫描 `MDoc.meta.tilts[].dataId → MovieResult.motion` 完成情况；齐则调 `KiwiWorkflowClient.completeUserTask(instanceId, "mdoc-motion-wait", vars)`。
5. kiwi-admin 新增 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 机机端点，由 Sa-Token 鉴权（与现有 movie 启动端点同模式），完成 active UserTask。
6. 提供联调可用的占位 BPMN `cryo-mdoc-minimal.bpmn`：`Start → ServiceTask:placeholder(Init) → UserTask(id=mdoc-motion-wait) → ServiceTask:placeholder(Continue) → End`。
7. 不破坏 movie 链路的现有运行时行为（仅 bean 名替换）。

**Non-Goals:**

- 不在 cryoems-bpm 中实现 mdoc 业务 `JavaDelegate`（属于后续 change）。
- 不实现"BPMN 内回查 cryoEMS Mongo 拿 MDoc/MDocMeta"的具体机制（瘦契约只携带 `task.id` + `mdoc.id`，业务 delegate 自行处理）。
- 不引入 BPMN message/signal 事件代替 UserTask 握手（已选 UserTask + 轮询 complete 方案）。
- 不修改 mdoc 导出链路（`ExportMdocEngine`/`MDocExportContext`/`MDocExportHandler`/`HandlerKey.MDOC_EXPORT`）。
- 不实现 `MdocMotionReadinessPoller` 独立 scheduler（决策选了"挂在 listener.onPoll 里"复用 watcher 节拍）。

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

### 决策 2：MotionWait 用 UserTask（`activityId="mdoc-motion-wait"`） + cryoEMS 轮询 complete

**Why**：

- BPMN 上把"等待前置 movie 完成"显式声明为节点，可视化清晰、Camunda 历史可追踪。
- UserTask 不需要 cryoEMS 端额外的 BPMN 修改即可"在外推动"（外部完成 UserTask 是 Camunda 原生能力）。
- 与本次"瘦契约 + 业务后续在 BPMN 里实现"的方向一致。

**Alternatives considered**：

- BPMN 内 Timer + ServiceTask 自轮询条件：需要在 BPMN 内具备"回查 cryoEMS"的能力（本次不实现 delegate），且 timer + condition 难以表达"齐了才走"。
- Message/Signal 事件由 movie 流程发给 mdoc：movie 流程不知道 mdoc 的 instanceId；需 cryoEMS 路由，反而复杂。
- cryoEMS 侧做"前置 readiness gate"，齐了才启动 mdoc 流程：把等待从 BPMN 中剔除，可读性下降；且若后续某些 mdoc 步骤在等待中也想消费 movie 结果，必须重新在 BPMN 表达。

**轮询归属**：挂在 `MdocKiwiWorkflowStateSyncListener.onPoll` 内（与 watcher 同节拍 5s），不新增 scheduler。

```
listener.onPoll(snapshot):
  1. 按 instanceId 加载 MDocInstance (隐式过滤)
  2. 写回 currentActivity / status / processing（与 movie 同形）
  3. ★ 若 currentActivity == "mdoc-motion-wait"：
        a. 取该 MDocInstance.data_id → 查 MDoc.meta.tilts[].dataId
        b. 按 dataId 批量查 MovieResult.motion 完成情况
        c. 全齐 → kiwiWorkflowClient.completeUserTask(
              instanceId, "mdoc-motion-wait", vars=Map.of())
        d. 不齐 → 下一轮再判
```

**风险**：

| Risk | Mitigation |
|------|-----------|
| `completeUserTask` 同步调用拖慢 onPoll | 第一版接受同步（mdoc 实例数通常远少于 movie，5s 节拍可容纳），如观测到延迟再切异步线程池 |
| 同一实例多次 complete（snapshot 还未刷新 currentActivity） | kiwi-admin 端 complete 必须幂等地处理"task 已完成"——返回稳定 4xx 而非 5xx；listener 看到 4xx 时记日志不重试 |
| readiness 判断与 movie 流程的 `MovieResult.motion` 写入存在时序裂缝 | 仅触发 complete，不删数据；下一轮若 movie 又写新结果不影响 |

### 决策 3：kiwi-admin 端 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete`

**契约**：

```
POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete
Headers: Authorization: Bearer <Sa-Token from PAT>
Body: { "variables": { ... } }   // 可空对象

Responses:
  200 OK     { ... 业务包装 }    — task 完成成功
  404 NOT_FOUND  — instance 不存在（已结束/已清理）
  409 CONFLICT   — instance 存在但无 activityId=taskKey 的 active UserTask
                  （可能尚未到达、已被完成、或是其他类型节点）
  其他           — 与现有 BPM 机机端点一致的错误体
```

**Why 用 path 上的 `taskKey` 而非 client 先查再传 `taskId`**：

- cryoEMS 不需要持久化 Camunda 内部 taskId；约定值 `mdoc-motion-wait` 跨实例稳定。
- 减少一次往返。
- Camunda `TaskService.createTaskQuery().processInstanceId(...).taskDefinitionKey(...)` 可直接定位。

**鉴权与现有端点一致**：复用 PAT → Sa-Token 兑换；同一 `BpmMachineIntegrationCtl`（或与之等价）下挂载。

### 决策 4：硬删 `MDocContext` / `MDocProcessor` / `MDocStep` 与全部 `Handler<MDocContext>`

**Why**：

- 这些类持有的"业务知识"在 BPMN delegate 实现期需要重新表达，留在 cryoEMS 反而成为"两份真相"。
- git history 是足够的参考来源。
- 本次硬删让编译失败暴露所有遗漏引用点，避免软删后零散的死代码。

**Trade-off**：

- 在 BPMN delegate 实现前，**mdoc 跑不出真实结果**（仅启动 + 卡在 UserTask 后又被 complete → 走到 ServiceTask placeholder → 结束）。这是预期效果，与 movie 当时的过渡状态一致。
- 若线上仍依赖旧 mdoc 链路出结果，本 change 应与 `cryoems-bpm-mdoc-javadelegate-migration` 一起发布；本 change 单独发布将令 mdoc 在过渡期无业务输出。

**HandlerKey 枚举值删除清单**：

```
MDocInit, MdodParser, MdocMotionWait, MovieConnect, MdocStack, MdocExclude,
MdocCoarseAlign, MdocPatchTracking, MdocSeriesAlign, AlignRecon, MDOC_SLURM
```

（仅删除对应 handler 已不存在的；`MDOC_EXPORT` 保留，导出链路不动。）

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
| `completeUserTask` 在 mdoc 实例还未到达 UserTask 时被触发（如刚启动） | listener 检查 `currentActivityId == "mdoc-motion-wait"` 之后才触发；snapshot 是 Kiwi 真实状态，避免误触 |
| `MovieResult.motion` 查询性能（大批量 tilts） | 与既有 `task/tilt/movie/MotionWait.handle` 等价的 `findByQuery(...in(ids))`，已是单次 in 查询；mdoc 数量级远小于 movie，无新增风险 |
| 硬删后 mdoc 导出 (`MDocExportHandler`) 的 `import com.cryo.task.tilt.MDocContext` 残留导致编译失败 | 任务清单中显式处理该 import 清理 |
| 跨仓 PR 节奏（cryo-em-server-backend 与 kiwi 仓） | `tasks.md` 按仓分组；联调前需要先合 kiwi 仓的 `completeUserTask` 端点 |

## Migration Plan

```
T0  归档/合并 kiwi-admin 端 POST .../tasks/{taskKey}/complete 端点
T1  归档/合并 cryo-em-server-backend：MDocInstance 字段 + Repository + Properties + KiwiWorkflowClient.completeUserTask
T2  归档/合并 cryo-em-server-backend：MdocKiwiWorkflowService / Variables / StateSyncListener
T3  归档/合并 cryo-em-server-backend：WorkflowIntegrationConfiguration bean 改名 + MovieEngine 同步替换
T4  归档/合并 cryo-em-server-backend：MdocEngine 改写 + 硬删 MDocContext 一坨
T5  部署 cryo-mdoc-minimal.bpmn（占位）到 Kiwi-admin；在 Task 上填 mdocProcessDefinitionId
T6  联调：tomo 数据集任务启动 → 观察 mdoc 实例启动 → UserTask 卡点 → 自动 complete → 终态
```

**回滚**：本 change 含 BREAKING 删除，无法纯配置回滚。回滚等价于 revert 到本 change 之前的 commits + 重新部署旧版本；BPMN 占位文件可保留，不影响旧版。

## Open Questions

- BPMN UserTask 的 `activityId` 是用 `"mdoc-motion-wait"` 这个 BPMN 节点 id（Camunda 等价于 `taskDefinitionKey`）？还是更长的命名空间如 `cryoems:mdoc-motion-wait`？
  - 倾向短 id；BPMN 节点 id 已经在 mdoc 流程内部独占命名空间。
  - 决议归 BPMN 占位骨架文件实现时（可在 tasks.md 中确认）。
- `KiwiWorkflowClient.completeUserTask` 的 `variables` 入参是否必须？
  - 第一版传空 Map；保留参数以便后续 BPMN 节点在 complete 时接收完成原因等信息。
