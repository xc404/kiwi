## ADDED Requirements

### Requirement: 按 instanceId + taskKey 完成 UserTask 的机机端点
kiwi-admin 后端 SHALL 在 BPM 集成 controller 下暴露 `POST /bpm/process-instance/{instanceId}/tasks/{taskKey}/complete` 端点。该端点 MUST 按 `instanceId` 与 `taskDefinitionKey == taskKey` 在 Camunda Task 服务中定位**唯一一条 active** 的 UserTask，并以请求体中的 `variables`（可为 null/空对象）调用 `TaskService.complete(taskId, variables)`。鉴权 MUST 与 `POST /bpm/process/{id}/start` 同模式（PAT 兑换 Sa-Token 后的 `Authorization: Bearer <token>` 头），且端点 MUST 暴露给具有 BPM 机机集成权限的调用方（与 movie 启动端点同一权限集）。

#### Scenario: 成功完成 active UserTask
- **WHEN** 调用方携带有效 Sa-Token 调用 `POST /bpm/process-instance/abc/tasks/mdoc-motion-wait/complete` body=`{"variables":{}}`
- **AND** 流程实例 abc 当前停在 `taskDefinitionKey=mdoc-motion-wait` 的 active UserTask
- **THEN** kiwi-admin MUST 调用 `TaskService.complete(taskId, Map.of())` 完成该 task
- **AND** 返回 HTTP 200 与统一业务响应包装（与现有 `POST /bpm/process/{id}/start` 同形）

#### Scenario: 实例不存在或已结束
- **WHEN** `instanceId` 在 Camunda 运行时表中不存在（已结束或未启动）
- **THEN** 端点 MUST 返回 HTTP 404，body 含可读错误信息说明实例不存在/已结束

#### Scenario: 实例存在但无匹配的 active UserTask
- **WHEN** 流程实例存在，但当前没有 `taskDefinitionKey == taskKey` 的 active UserTask（例如还未到达、已经完成、或当前节点不是 UserTask）
- **THEN** 端点 MUST 返回 HTTP 409，body 含错误码与可读信息（如 `"no active user task with key 'mdoc-motion-wait' on instance 'abc'"`）

#### Scenario: 入参 variables 透传
- **WHEN** 请求体为 `{"variables":{"reason":"ready","count":7}}`
- **THEN** Camunda Task 完成后 MUST 在流程变量中可读到 `reason="ready"`、`count=7`

#### Scenario: 入参 variables 缺省
- **WHEN** 请求体为 `{}` 或 `{"variables":null}`
- **THEN** 端点 MUST 等价于以空 Map 完成 task（不抛 NPE，不影响其他流程变量）

#### Scenario: 未授权调用
- **WHEN** 请求未携带 `Authorization` 头，或携带的 token 无效/过期
- **THEN** 端点 MUST 返回 HTTP 401/403（与现有 BPM 机机端点统一），且不执行任何 Camunda 操作

#### Scenario: 同 instance 存在重复 taskKey 的 active UserTask
- **WHEN** 由于流程设计或多分支并行原因，同一 `instanceId` 下存在多条 `taskDefinitionKey == taskKey` 的 active UserTask
- **THEN** 端点 MUST 拒绝执行并返回 HTTP 409，body 含错误信息（如 `"ambiguous: multiple active user tasks with key '<key>' on instance '<id>'"`）；MUST NOT 任选一条完成
