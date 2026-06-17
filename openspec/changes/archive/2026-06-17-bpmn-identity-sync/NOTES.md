# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/bpmn_identity_sync_47318153.plan.md`；**已实现**后归档，plan 文件已删除。

## 实现路径（以代码为准）

- `BpmProcessDefinitionService.syncBpmnIdentity(BpmProcess)`：DOM 同步 `<bpmn:process id>` 与全部 `BPMNPlane@bpmnElement`
- 保存/部署有修正时返回 **`RWarning.of(bpmProcess, BpmnIdentityCorrectedMsg)`**（非 plan 草案中的 `R.success().setMsg()`）
- 前端 `BaseHttpService` 通过 **`warningCode`** 展示 `item.msg`（非 plan 草案中的 `showServerMsg` 选项）
- 单测：`BpmProcessDefinitionServiceTest`

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
