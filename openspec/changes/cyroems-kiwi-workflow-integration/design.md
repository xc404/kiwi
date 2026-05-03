## 上下文

- **cryoEMS**：Mongo 中 `Movie` / `Task` / `TaskDataset`；历史上由 `MovieEngine` + `FlowManager` 驱动步骤。
- **kiwi-admin**：Camunda、`BpmProcess`、启动与限额（如 `maxProcessInstances`）、机机集成密钥。

## 目标架构（契约修正后）

```
[Task Mongo] ──► kiwiMovieBpmProcessId（Kiwi 库中 BpmProcess.id，任务级）
       │
       ▼
[MovieEngine / MovieKiwiWorkflowService]
       │  载入 Task、TaskDataset、Movie
       │  组装 variables（不少于 movieId、cryoTaskId；可按 BPMN 扩展）
       │
       ▼
[KiwiWorkflowClient]
       │  POST …/integration/process/{bpmProcessId}/start
       │  若 429（或契约内「限流」语义）► 间隔 sleep ► 重试（≤ maxAttempts）
       ▼
[Kiwi Camunda]
       │
[cryoEMS Mongo]   movie.external_workflow_instance_id = Camunda processInstanceId
```

- **编排权威**：Kiwi BPMN（当前可为空壳）。
- **流程选择权威**：按 **Task** 配置的 Kiwi `BpmProcess` 主键，而非全局单一的 `movie-process-definition-id`。
- **cryoEMS**：不写「回调执行业务」路径；负责 **启动**、**关联 id**、以及与 Kiwi 对齐的 **重试**。（已移除错误的 Kiwi Delegate → cryoEMS step 实现。）

## Task 模型

- 在 **`Task`** 上增加字段（命名实现阶段二选一，建议其一）：
  - **`kiwiMovieBpmProcessId`**：字符串，对应 Kiwi `BpmProcess.id`，用于 movie 流水线启动该 Task 下影片时选用流程。
  - 若历史数据为空：可由运维/API 回填，或短期保留「读全局配置回退」策略（仅迁移期，实现时在代码注释与 `tasks.md` 勾选中注明下线条件）。

## 启动变量（扩展）

门面 **`MovieKiwiWorkflowService`**（或等价）在调用 `KiwiWorkflowClient.startProcess(bpmProcessId, variables)` 前，必须能访问：

| 上下文 | 用途示例 |
|--------|-----------|
| `Movie` | `movieId`、`external_workflow_instance_id` 判断是否已启动 |
| `Task` | `task.id`、`kiwiMovieBpmProcessId`（或选定字段）、任务元数据 |
| `TaskDataset` | 数据集/管线相关键值，按需映射进 Camunda 变量（与 BPMN 约定一致） |

最小变量集仍建议包含：

| 变量 | 含义 |
|------|------|
| `movieId` | Mongo `Movie.id` |
| `cryoTaskId` | Mongo `Task.id` |

其余字段由实现与 BPMN 共同约定；本 design 不要求首版 Handler 全部消费。

## KiwiWorkflowClient 行为

1. **禁止**使用本地 `Semaphore` / `concurrentPermits` 作为「与 Kiwi 对齐」的并发闸门。
2. **限额语义**：以 kiwi-admin **`POST .../start`** 为准；运行中实例数已达 `maxProcessInstances` 时返回 **429**，body 稳定（便于客户端识别「可重试」而非配置错误）。不提供单独的 capacity 查询机机接口。
3. **启动与重试**：
   - 直接 `POST .../start`；
   - 若响应为 **429**（或契约规定的限流 HTTP 状态 + body），按 **`app.kiwi.workflow.client`（或等价前缀）** 中的 **`rateLimitRetryIntervalMillis`**、`maxStartAttempts`（命名实现可微调）等待后重试；
   - 在未超过 **最大尝试次数** 前循环；超限则向上抛出，由调度侧打日志。
4. **查询实例状态**：沿用或扩展已有 `GET .../process-instances/{id}/state` 类机机接口（若有），与本 change 并行存在即可。

### cryoEMS 配置（示例）

**全局**（连接与客户端行为；**不再**承担「唯一流程 id」职责）：

```yaml
app:
  kiwi:
    workflow:
      enabled: true
      base-url: http://kiwi-admin:8088
      integration-secret: <与 Kiwi kiwi.integration.machine.secret 一致>
      client:
        rate-limit-retry-interval-millis: 1000
        max-start-attempts: 5
        # movie-process-definition-id 仅作迁移期回退时可保留，默认为空
```

**任务级**：在 **`Task`** 文档中填写 `kiwiMovieBpmProcessId`（或选定字段）。

**kiwi-admin**：`kiwi.integration.machine.secret` 等与现有一致。

## 占位 BPMN

- 可导入 `assets/cryo-movie-minimal.bpmn`。在 Kiwi 中新建流程并 **部署**；将库中该记录的 **`BpmProcess.id`** 写入对应 **cryoEMS `Task`** 字段（不再要求填 cryoEMS 全局 `movie-process-definition-id`）。

## 安全

- 机机调用使用共享密钥（如 `X-Kiwi-Integration-Secret`）；start、state 查询等同属机机契约，密钥一致。
- 限制网络可达性（运维侧）。
