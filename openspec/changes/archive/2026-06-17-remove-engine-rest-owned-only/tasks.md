# Tasks

## 1. 后端 API 与所有权

- [x] `BpmHistoric*` DTO + `BpmProcessInstanceService` definition-xml / history-activities / variables
- [x] `BpmOwnershipAccessService`；流程 CRUD/列表与实例 get/page/子资源接入
- [x] `BpmProcessInstanceCtl` 三个子资源端点 + 所有权校验

## 2. engine-rest 收口

- [x] `kiwi.bpm.engine-rest-http-enabled` + `EngineRestHttpBlockConfiguration`
- [x] `SaTokenConfigure` 移除 `/engine-rest/**` 白名单
- [x] `backend/README.md` 补充默认关闭说明

## 3. 前端

- [x] `process-instance.service` / `bpm-viewer` 改调 `/bpm/*`；清理 `camundaEngineRestPath`

## 4. 验证

- [ ] 跨用户所有权隔离与 cryoEMS 集成冒烟（手工清单，无自动化记录）
