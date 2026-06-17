## Why

BPM 设计器画布可从调色板拖入「调用活动」（`bpmn:CallActivity`），此时元素**没有**组件库 `componentId`，属性面板仍展示只读的 `component-selector`（组件流程），用户无法绑定到已保存的业务流程。与从组件库拖入的 Call Activity 相比，两条路径未区分。同时，无组件绑定的 Call Activity 需要像「自定义输出」一样维护**自定义输入**（写入 `camunda:inputParameter`），并在创建时默认开启 **Propagate all variables**。

## What Changes

- **有 `componentId`**：保持现有行为——只读 `component-selector`、组件契约驱动的「输入」「输出」Tab、既有自定义输出区；组件声明的输入仍走目录分组，非声明项走自定义输入区（与自定义输出对称）。
- **无 `componentId`**：在「流程」分组展示可编辑 **「选择流程」**（`processId`，`process-selector`），选项为当前用户可见的已保存流程（`GET /bpm/process`）。
- **本变更不包含**：选择流程后根据 `as-component` 加载子流程输入/输出契约 Tab；**不**在本变更内依赖 `calledElement` 与 `setProcessId` 的联动（`processId` 仅作设计期引用，运行时绑定后续迭代）。
- **Call Activity 自定义输入**：在「输入」Tab 增加「自定义输入」折叠区（交互对齐 `bpm-custom-outputs-panel`），增删改行并持久化为 `camunda:InputOutput` 下的 `inputParameter`；有组件绑定时排除目录 `inputParameters` 的 key，无组件时全部为自定义行。
- **默认 Propagate all variables**：从调色板新建或初始化的 Call Activity SHALL 调用既有 `ensurePropagateAllVariables`（`camunda:In` / `camunda:Out` 的 `variables="all"`）。
- 沿用 `isCallActivityProcessPick`（`call-activity-bindings.ts`）区分流程选择路径与组件路径。

## Capabilities

### New Capabilities

- `call-activity-process-picker`：无 `componentId` 时通过可见流程列表选择并持久化 `processId`（不含 as-component / calledElement 契约加载）。
- `call-activity-custom-inputs`：Call Activity 在属性面板「输入」Tab 提供自定义输入编辑器，写入 `inputParameter`，并与组件声明输入去重。

### Modified Capabilities

- （无）`openspec/specs/` 下尚无 BPM 属性面板基线规格。

## Impact

- **前端**：`component-property-provider.ts`、流程 `process-selector`、`properties-panel`（输入 Tab + `bpm-custom-inputs-panel`）、`base-pallete-provider.initElement`（propagate all）；**不**扩展 `ComponentService.getComponentForElement` 的 `as-component` 分支。
- **后端**：复用 `GET /bpm/process` 列表；**不**依赖 `GET /bpm/process/{id}/as-component` 于本变更。
- **BPMN**：`processId` 扩展属性；`inputParameter` 自定义映射；`camunda:In`/`Out` `variables=all` 默认值。
