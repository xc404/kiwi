## 任务（本 change 最小范围）

- [x] 撰写 proposal / design / tasks（`openspec/changes/cyroems-kiwi-workflow-integration/`）
- [x] kiwi-admin：`POST /bpm/process/{id}/start` 支持可选流程变量；`BpmProcessStartService`
- [x] kiwi-admin：机机启动 `POST /bpm/integration/process/{id}/start`（共享密钥）
- [x] cryoEMS：movie 调度 **仅** 调用 Kiwi 启动流程并写入 `external_workflow_instance_id`（`MovieEngine` + `KiwiWorkflowStarter`）
- [x] Kiwi：占位 BPMN `cryo-movie-minimal.bpmn`（assets + `kiwi-admin/.../bpm/samples/`），导入后部署即可启动
- [x] 运维：`application.yml` 增加 `app.kiwi.workflow.*` 默认项（`design.md` 密钥说明）；生产需填写 base-url、`movie-process-definition-id`、`integration-secret` 并启用

## 明确延后（不要求本 change）

- [ ] kiwi-admin：在 BPMN 中 **实现** 真实 movie 处理步骤（你以后做）
- [ ] cryoEMS：流程状态监听、与 Camunda 历史同步

## 已移除（错误实现，非本 change 范围）

- ~~`CryoemsMoviePipelineDelegate`（Kiwi → HTTP → cryoEMS）~~  
- ~~cryoEMS `POST /internal/workflow/step` 及关联 Service~~
