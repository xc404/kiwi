## 摘要

将 cryoEMS **单颗粒 movie** 的调度从本地 `FlowManager`/`InstanceProcessor` 链路，改为 **调用 kiwi-admin（Camunda）的流程**：在合适的时机 **启动流程实例**，并写入 `external_workflow_instance_id` 等与 Kiwi 实例的关联。

本 change **不包含**：由 Kiwi HTTP 回调 cryoEMS、在 cryoEMS 内按步执行业务的集成（对应错误实现已从仓库移除）。  
**不要求在本阶段实现原先 movie 的完整处理功能**——Kiwi 侧 BPMN 可先占位或仅含空/日志步骤；**具体业务步骤以后再在 kiwi-admin 实现**。

### 契约修正（相对初版 propose）

以下与首轮 OpenSpec / 初版实现对齐方式不同，**以本节为准**：

1. **流程定义主键来源**：`movieProcessDefinitionId`（或语义等价的字段名，表示 Kiwi 侧 `BpmProcess` 主键）**不应**放在 cryoEMS 全局 `application.yml` 作为唯一来源，而应 **配置在 `Task`（Mongo 实体）上**，按任务维度选择 Kiwi 流程；全局仅保留 Kiwi 连接与客户端行为类配置（base-url、密钥、重试等）。
2. **Movie 启动入参**：`MovieKiwiWorkflowService`（或等价门面）在调用 `startProcess` 组装变量时，必须能使用 **`Task`、`TaskDataset`（及 `Movie`）** 的上下文，而不仅是 `movieId` / `cryoTaskId` 两个标量；具体变量清单在实现阶段与 BPMN 约定一致即可在 proposal 中枚举。
3. **限流与容量**：`KiwiWorkflowClient` **不得**在本地用 `Semaphore`/`concurrentPermits` 自定义并发上限来代替服务端策略。服务端以 **`POST .../start` + `maxProcessInstances`** 为准；达上限时 kiwi-admin 返回 **429**，客户端按配置 **重试**，不再单独提供 capacity 查询机机接口。
4. **启动重试**：`startProcess` 应先发起请求；若 kiwi-admin 返回 **明确的服务端限流语义**（例如 HTTP **429**，或契约规定的错误码/body），则在客户端配置的 **重试间隔** 后重试，且不超过配置的 **最大尝试次数**（指数退避可作为可选项，首版可为固定间隔）。

本 change **仍不包含**：由 Kiwi HTTP 回调 cryoEMS、在 cryoEMS 内按步执行业务（若后续需要另起任务）。

## 动机

- 编排入口统一到 Kiwi；cryoEMS 专注触发流程与本地数据主键。
- 降低一次性迁移成本：先打通 **启动 + 关联 id**，再迭代 BPMN 内容。
- **任务级流程绑定**：不同 Task 可指向不同 Kiwi 流程，避免全局单一路径定义 id。
- **限流与服务端对齐**：以 Kiwi 启动接口返回的 **429** 为准，客户端配合重试而非本地盲目限并发。

## 范围

**必须交付（最小闭环）**

- **kiwi-admin**：
  - 支持带流程变量启动实例；提供 cryoEMS 机机可调用的 **启动流程** 能力（如 `POST /bpm/integration/process/{id}/start` + 共享密钥）。
  - 启动时若已达 `maxProcessInstances`，返回 **可识别的限流响应**（如 429 + 一致 body），与客户端重试策略配套（不提供单独的 capacity 查询机机接口）。
  - （若已有）查询流程实例状态的机机接口，供监听/轮询使用。
- **cryoEMS**：
  - **`Task`** 模型支持存储 Kiwi `BpmProcess` 主键（任务级配置）。
  - movie 调度侧调用启动接口时传入 **`task`、`taskDataset`、`movie`** 等上下文组装变量；持久化 `external_workflow_instance_id`。
  - **`KiwiWorkflowClient`**：无本地 Semaphore 代替服务端限额；**遇 429 时按配置重试**。
  - 移除/不再依赖全局 `app.kiwi.workflow.movie-process-definition-id` 作为唯一流程选择来源（可保留迁移期兼容策略由实现阶段在 design 中定）。

**明确不在本 change 范围**

- 在 kiwi-admin **实现** 与旧 cryoEMS 等价的 movie 处理业务（运动校正、CTF、Slurm 等）——**后续你再做**。

## 非目标

- mdoc / export 等其它管线（除非另起任务）。
- 用本地信号量伪装「与服务端一致的并发限额」（已由契约修正禁止）。

## 风险与假设

- 未配置密钥或流程未部署时，启动会失败；需在运维侧显式配置。
- 依赖重试与 Kiwi 返回的限流语义兜底。
- 占位 BPMN 结束即「完成」，与真实业务完成语义不同，直到你在 Kiwi 中实现真实步骤。
