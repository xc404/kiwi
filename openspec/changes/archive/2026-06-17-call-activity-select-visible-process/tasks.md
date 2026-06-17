## 1. 分支与流程选择

- [x] 1.1 `ComponentPropertyProvider.buildBindingGroups`：`isCallActivityProcessPick` 时 `processId` + `process-selector`；否则只读 `component-selector`
- [x] 1.2 流程选择写值走 `element`/`processId`，**不**调用 `setProcessId` / 不写 `calledElement`
- [x] 1.3 新增 `bpm-process-selector` + Formly `process-selector`；列表 `GET /bpm/process`，排除当前流程 id

## 2. 自定义输入

- [x] 2.1 新增 `bpm-custom-inputs-panel`（镜像 `custom-outputs-panel`），`flush` 读写 `PropertyNamespace.inputParameter`
- [x] 2.2 `properties-panel`：「输入」Tab + Call Activity 时展示「自定义输入」折叠区
- [x] 2.3 无 `componentId` 时 `ComponentPropertyProvider` 不依赖组件目录生成「输入」分组（仅自定义输入 + 流程选择）

## 3. Propagate all variables 默认

- [x] 3.1 `base-pallete-provider.initElement` 对 Call Activity 调用 `ensurePropagateAllVariables`
- [x] 3.2 确认组件库拖入路径已具备 propagate all（`setComponentId`），不重复破坏

## 4. 验证

- [x] 4.1 调色板 Call Activity：默认 In/Out `variables=all`；可选流程；自定义输入写入 `inputParameter`；无 as-component 请求
- [x] 4.2 组件库 Call Activity：只读组件流程；声明输入 + 自定义输入去重；自定义输出仍可用
- [x] 4.3 前端构建/lint 通过
