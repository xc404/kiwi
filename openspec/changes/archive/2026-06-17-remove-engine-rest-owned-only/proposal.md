## Why

前端 `bpm-viewer` 经 `camundaEngineRestPath` 直连 `/engine-rest`，绕过 Kiwi 鉴权与业务封装。需收口为 `/bpm/*` API，并按「本人创建的流程 / 本人流程下的实例」做访问控制；同时默认关闭对外 `engine-rest` HTTP。

## What Changes

- 新增流程实例只读子资源：`definition-xml`、`history-activities`、`variables`
- `BpmOwnershipAccessService`：`createdBy` 所有权；列表过滤；可选 `bpm:admin` 放行
- `kiwi.bpm.engine-rest-http-enabled`（默认 `false`）+ `EngineRestHttpBlockConfiguration`
- 前端移除 engine-rest 依赖，改调 `/bpm/process-instance/*`
- `SaTokenConfigure` 不再白名单 `/engine-rest/**`

## Capabilities

### New Capabilities

- （无 main spec。）

### Modified Capabilities

- （无。）

## Impact

- `BpmProcessInstanceCtl`、`BpmProcessDefinitionCtl`、`BpmProcessInstanceService`
- `BpmOwnershipAccessService`、`EngineRestHttpBlockConfiguration`、`KiwiBpmProperties`
- `process-instance.service.ts`、`bpm-viewer.ts`、`environment*.ts`

## 非目标

- BPM 项目成员 / 跨用户协作
- Operaton 引擎内置 authorization
- `/camunda/**` Webapp 收口
