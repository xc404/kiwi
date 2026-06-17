# Tasks

## 1. 后端

- [x] `BpmProcessDefinitionService`：DOM 版 `syncBpmnIdentity`，替换 `updateIdAndName`
- [x] `BpmProcessDefinitionCtl` save/deploy 接入 sync；修正时 `RWarning.of(..., BpmnIdentityCorrectedMsg)`
- [x] `BpmProcessDefinitionServiceTest`：对齐 / 不变 / 非法 XML

## 2. 前端

- [x] `BaseHttpService`：`warningCode` 展示服务端 `msg`
- [x] `process-design.service` 保存/部署接入
- [x] `bpm-editor` 保存/部署成功后 `importXML` 回灌
