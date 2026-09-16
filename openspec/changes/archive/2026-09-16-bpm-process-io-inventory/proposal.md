## Why

流程 IO 分析原先只给出扁平的 `processInputs` / `processOutputs`，不按组件拓扑展开，也不区分字面量、`${var}` 与空值；必填参数未写入 XML、以及 Designer 写入的 `kiwi:componentId` 都会漏掉。启动弹窗只能贴空白 JSON，Designer Agent 只能靠整份 XML 猜缺参。

## What Changes

- 新增设计时 **流程 IO 清单** `BpmProcessIoInventory`：按 `sequenceFlow` 拓扑序列出每个绑定组件的 input/output，并标出已填/未填、字面量 vs 表达式、是否被上游产出覆盖。
- `BpmProcessIoAnalysisService.analyzeInventory` 成为唯一实现；`analyzeComponentIoGaps` / `wrapProcessAsComponent` 从清单派生，避免两套图遍历。
- REST：`GET /bpm/process/{id}/io-inventory`、`POST /bpm/process/io-inventory`（画布未保存 XML）。
- Designer Agent 增加工具 `list_process_io`。
- 启动流程弹窗用 `startVariables` 生成 JSON 骨架，与 localStorage 合并，并提示仍为空的 key；**仍用 JSON 编辑器**。

## Capabilities

### New Capabilities

- `bpm-process-io-inventory`：设计时按组件拓扑列出流程 IO、启动缺口，以及 REST / 启动弹窗 / Agent 工具的消费约定。

### Modified Capabilities

- （无。现有 `as-component` 仍返回扁平契约，仅改为从清单派生，不改变对外需求条文。）

## Impact

- **后端**：`BpmProcessIoInventory`、`BpmProcessIoAnalysisService`、`BpmProcessDefinitionCtl`、`DesignerHarnessTools` / `DesignerHarnessTurnLlm`。
- **前端**：`bpm-process-io-inventory.ts`、`process-design.service`、`BpmStartVariablesService.mergeSkeleton`、启动弹窗 `missingKeys`。
- **不做**：流程实例运行时变量、用户任务表单/网关条件中的 `${}`、启动弹窗改成表单（后续独立 change）、项目环境变量是否已有值。
