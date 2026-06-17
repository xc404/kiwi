## Why

保存流程时若仅更新 `bpmnXml` 而不改名，旧的 `updateIdAndName` 不会执行；且正则仅匹配固定 `BPMNPlane_1`，无法可靠对齐 bpmn-js 生成的 plane id。导致 `BpmProcess.id` 与 BPMN 内 `process` / `BPMNPlane@bpmnElement` 不一致，部署与引擎行为异常。

## What Changes

- 以 `BpmProcess.id` 为唯一权威，在保存、部署、克隆等写入路径用 **DOM** 自动修正 BPMN identity
- 修正发生时通过统一响应 **`msg`** 提示前端（warning 语义），画布静默回灌服务端 XML
- 新增 `BpmProcessDefinitionServiceTest` 覆盖对齐/不变/非法 XML

## Capabilities

### New Capabilities

- （无 main spec；本变更为实现修复。）

### Modified Capabilities

- （无。）

## Impact

- `BpmProcessDefinitionService`、`BpmProcessDefinitionCtl`
- `RWarning`、前端 `BaseHttpService.warningCode`
- `process-design.service.ts`、`bpm-editor.ts`

## 非目标

- 禁止用户在 bpmn-js 编辑 process id（后端兜底即可）
- 历史脏数据批量迁移脚本（部署时自动修正）
