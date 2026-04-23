## 摘要

将 cryoEMS **单颗粒 movie** 的调度从本地 `FlowManager`/`InstanceProcessor` 链路，改为 **调用 kiwi-admin（Camunda）的流程**：在合适的时机 **启动流程实例**，并写入 `external_workflow_instance_id` 等与 Kiwi 实例的关联。

本 change **不包含**：由 Kiwi HTTP 回调 cryoEMS、在 cryoEMS 内按步执行业务的集成（对应错误实现已从仓库移除）。  
**不要求在本阶段实现原先 movie 的完整处理功能**——Kiwi 侧 BPMN 可先占位或仅含空/日志步骤；**具体业务步骤以后再在 kiwi-admin 实现**。

## 动机

- 编排入口统一到 Kiwi；cryoEMS 专注触发流程与本地数据主键。
- 降低一次性迁移成本：先打通 **启动 + 关联 id**，再迭代 BPMN 内容。

## 范围

**必须交付（最小闭环）**

- **kiwi-admin**：支持带流程变量启动实例；提供 cryoEMS 机机可调用的 **启动流程** 能力（如 `POST /bpm/integration/process/{id}/start` + 共享密钥）。
- **cryoEMS**：movie 调度侧 **仅** 调用上述接口启动 Kiwi 流程，并持久化外部流程实例 id（及启动变量如 `movieId`、`cryoTaskId`）；配置项（base-url、流程定义 id、密钥）可用。
- **Kiwi 侧**：存在已 **部署** 的 `BpmProcess`（BPMN 可为占位，能启动即可）。

**明确不在本 change 范围**

- 在 kiwi-admin **实现** 与旧 cryoEMS 等价的 movie 处理业务（运动校正、CTF、Slurm 等）——**后续你再做**。

## 非目标

- mdoc / export 等其它管线（除非另起任务）。
- cryoEMS 流程状态监听、Webhook（可后续 change）。

## 风险与假设

- 未配置密钥或流程未部署时，启动会失败；需在运维侧显式配置。
- 占位 BPMN 结束即「完成」，与真实业务完成语义不同，直到你在 Kiwi 中实现真实步骤。
