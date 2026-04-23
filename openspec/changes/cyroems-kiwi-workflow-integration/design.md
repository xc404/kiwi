## 上下文

- **cryoEMS**：Mongo 中 `Movie` / `Task`；历史上由 `MovieEngine` + `FlowManager` 驱动步骤。
- **kiwi-admin**：Camunda、`BpmProcess` 存储与部署、流程实例启动 API。

## 目标架构（本 change，无过渡层）

```
[cryoEMS MovieEngine 或等价调度]
    │  机机：POST .../bpm/integration/process/{BpmProcess.id}/start
    │  Body: { "variables": { "movieId", "cryoTaskId", ... } }
    ▼
[Kiwi Camunda]   ← BPMN 可先为 Start → End，或单个 script/log 步骤；**业务步骤以后再补**
    │
[cryoEMS Mongo]  movie.external_workflow_instance_id = Camunda processInstanceId
```

- **编排权威**：Kiwi BPMN（当前可为空壳）。
- **cryoEMS**：不写「回调执行业务」路径；只负责 **启动** 与 **关联 id**。（已移除错误的 Kiwi Delegate → cryoEMS step 实现。）

## 启动变量（建议）

| 变量 | 含义 |
|------|------|
| `movieId` | cryoEMS Mongo `Movie.id` |
| `cryoTaskId` | cryoEMS `Task.id` |

可按需在 Kiwi 流程中扩展；本阶段不要求 Handler 消费。

## 安全

- 机机启动使用共享密钥（如 `X-Kiwi-Integration-Secret`），与 cryoEMS 配置一致；限制网络可达性。

## 配置（示例）

**cryoEMS**

```yaml
app:
  kiwi:
    workflow:
      enabled: true
      base-url: http://kiwi-admin:8088
      movie-process-definition-id: "<BpmProcess.id>"
      integration-secret: <与 Kiwi kiwi.integration.machine.secret 一致>
```

**kiwi-admin**：`kiwi.integration.machine.secret` 等与现有一致。

## 占位 BPMN

- 可导入 `assets/cryo-movie-minimal.bpmn`（同内容在 `kiwi-admin/backend/src/main/resources/bpm/samples/cryo-movie-minimal.bpmn`）。在 Kiwi 中 **新建流程** 后粘贴/保存，**部署**；将库中该流程的 **`BpmProcess.id`** 填到 cryoEMS `app.kiwi.workflow.movie-process-definition-id`（与 BPMN 内 `bpmn:process id` 不要求相同，以 Kiwi 库里流程主键为准）。
