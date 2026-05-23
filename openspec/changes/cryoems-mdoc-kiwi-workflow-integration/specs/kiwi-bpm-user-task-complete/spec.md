## ADDED Requirements

### Requirement: 按 instanceId + taskKey 推进 BPMN 等待节点的机机端点
kiwi-admin 后端 SHALL 在 BPM 集成 controller 下暴露 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 端点。该端点 MUST 按 `instanceId` 与 `taskKey` 在 Camunda 中按以下优先级定位**唯一一条**可推进的等待节点并推动流程：

1. `TaskService.createTaskQuery().processInstanceId(instanceId).taskDefinitionKey(taskKey).active().list()` 命中 1 条 → 以请求体中的 `variables`（可为 null/空对象）调用 `TaskService.complete(taskId, variables)`（UserTask 路径）。
2. 否则 `ManagementService.createJobQuery().processInstanceId(instanceId).activityId(taskKey).list()` 命中 1 条 → 若 `variables` 非空，先 `RuntimeService.setVariables(execution, variables)`，再 `ManagementService.executeJob(jobId)`（覆盖 ManualTask `camunda:asyncBefore="true"` 等以 async-continuation Job 形式停泊的等待节点）。
3. 两条路径都未命中或任一路径命中多条 → 拒绝。

鉴权 MUST 与 `POST /bpm/process/{id}/start` 同模式（PAT 兑换 Sa-Token 后的 `Authorization: Bearer <token>` 头），且端点 MUST 暴露给具有 BPM 机机集成权限的调用方（与 movie 启动端点同一权限集）。

#### Scenario: 成功完成 active UserTask
- **WHEN** 调用方携带有效 Sa-Token 调用 `POST /bpm/process-instance/abc/tasks/mdoc-motion-wait/complete` body=`{"variables":{}}`
- **AND** 流程实例 abc 当前停在 `taskDefinitionKey=mdoc-motion-wait` 的 active UserTask
- **THEN** kiwi-admin MUST 调用 `TaskService.complete(taskId, Map.of())` 完成该 task
- **AND** 返回 HTTP 200 与统一业务响应包装（与现有 `POST /bpm/process/{id}/start` 同形）

#### Scenario: 成功推进 ManualTask asyncBefore Job
- **WHEN** 调用方携带有效 Sa-Token 调用 `POST /bpm/process-instance/abc/tasks/mdoc-motion-wait/complete` body=`{"variables":{}}`
- **AND** 流程实例 abc 当前在 `activityId=mdoc-motion-wait` 的 ManualTask 上有一条 async-continuation Job（且无同 `taskDefinitionKey` 的 active UserTask）
- **THEN** kiwi-admin MUST 调用 `ManagementService.executeJob(jobId)` 推进流程
- **AND** 返回 HTTP 200 与统一业务响应包装

#### Scenario: 实例不存在或已结束
- **WHEN** `instanceId` 在 Camunda 运行时表中不存在（已结束或未启动）
- **THEN** 端点 MUST 返回 HTTP 404，body 含可读错误信息说明实例不存在/已结束

#### Scenario: 实例存在但无匹配等待节点
- **WHEN** 流程实例存在，但当前既无 `taskDefinitionKey == taskKey` 的 active UserTask，也无 `activityId == taskKey` 的 async-continuation Job（例如还未到达、已经完成、或当前节点不是等待节点）
- **THEN** 端点 MUST 返回 HTTP 409，body 含错误码与可读信息（如 `"no active user task and no async-continuation job with key 'mdoc-motion-wait' on instance 'abc'"`）

#### Scenario: 入参 variables 透传（UserTask 路径）
- **WHEN** 请求体为 `{"variables":{"reason":"ready","count":7}}` 且命中 UserTask
- **THEN** Camunda Task 完成后 MUST 在流程变量中可读到 `reason="ready"`、`count=7`

#### Scenario: 入参 variables 透传（ManualTask Job 路径）
- **WHEN** 请求体为 `{"variables":{"reason":"ready"}}` 且命中 async-continuation Job
- **THEN** 在 `executeJob` 之前 MUST 通过 `RuntimeService.setVariables(executionId, variables)` 写入 `reason="ready"` 到对应 execution

#### Scenario: 入参 variables 缺省
- **WHEN** 请求体为 `{}` 或 `{"variables":null}`
- **THEN** 端点 MUST 等价于以空 Map 推进（不抛 NPE，不影响其他流程变量；ManualTask 路径下不调用 `setVariables`）

#### Scenario: 未授权调用
- **WHEN** 请求未携带 `Authorization` 头，或携带的 token 无效/过期
- **THEN** 端点 MUST 返回 HTTP 401/403（与现有 BPM 机机端点统一），且不执行任何 Camunda 操作

#### Scenario: 同 instance 存在重复 taskKey 的 active UserTask
- **WHEN** 由于流程设计或多分支并行原因，同一 `instanceId` 下存在多条 `taskDefinitionKey == taskKey` 的 active UserTask
- **THEN** 端点 MUST 拒绝执行并返回 HTTP 409，body 含错误信息（如 `"ambiguous: multiple active user tasks with key '<key>' on instance '<id>'"`）；MUST NOT 任选一条完成

#### Scenario: 同 instance 存在重复 activityId 的 async-continuation Job
- **WHEN** UserTask 路径未命中，但同一 `instanceId` 下存在多条 `activityId == taskKey` 的 async-continuation Job
- **THEN** 端点 MUST 拒绝执行并返回 HTTP 409，body 含错误信息（如 `"ambiguous: multiple async-continuation jobs with activity id '<key>' on instance '<id>'"`）；MUST NOT 任选一条执行
