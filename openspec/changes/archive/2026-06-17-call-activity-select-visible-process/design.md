## Context

- `ComponentPropertyProvider` 对 `bpmn:CallActivity` 固定展示只读 `componentId`；无 `componentId` 时无流程可选。
- 「输出」Tab 已有 `bpm-custom-outputs-panel`，通过 `PropertyNamespace.outputParameter` 读写 `camunda:InputOutput` 的 `outputParameters`，并过滤组件目录声明 key。
- `CamundaElementModel` 已支持 `inputParameter` / `outputParameter` 读写、`ensurePropagateAllVariables`、`processId` 与 `componentId` 互斥；`setProcessId` 会写 `calledElement`——**本变更不使用该路径**。
- 调色板 Call Activity 的 `initElement` 当前为空，未默认 propagate all。

## Goals / Non-Goals

**Goals:**

- `componentId` 有无分支属性面板；无组件时可选可见流程（`processId`）。
- Call Activity 在「输入」Tab 提供与自定义输出对称的**自定义输入**，持久化 `inputParameter`。
- 新建 Call Activity 默认 `propagate all variables`（`variables="all"`）。

**Non-Goals:**

- **不**调用 `getProcessAsComponent` / `as-component` 填充输入输出 Tab。
- **不**在本变更写入或维护 `calledElement`（即使存在 `setProcessId` 实现也暂不接入流程选择器）。
- 不改变组件库拖入 Call Activity 的 `componentId` / `setComponentId` 行为（含其自带的 `calledElement` 与 propagate all）。
- `camunda:In` / `Out` 逐变量映射 UI（除 propagate all 默认外）不在本变更扩展。

## Decisions

1. **分支判据**
   - `isCallActivityProcessPick`：`componentId` 为空 → 展示 `processId` + `process-selector`；否则只读 `component-selector`。

2. **流程列表**
   - `GET /bpm/process` 分页 `content`；可选 `projectId`；排除当前编辑流程 id。

3. **流程选择持久化**
   - 经 `ElementModel.setValue(..., 'element', 'processId', id)` 写入 `camunda:Properties`；**不**调用 `setProcessId`，避免 `calledElement` 与 as-component 前提。
   - 若需与 `componentId` 互斥，在 element 层清除对侧扩展属性（与现 `CamundaElementModel` 规则对齐，但不触发 `calledElement` 更新）。

4. **自定义输入**
   - 新建 `bpm-custom-inputs-panel`（结构镜像 `custom-outputs-panel`）：行 `{ name, valueText }`；`flush` 时 `setValue(..., inputParameter, name, valueText)` / `removeInputParameter`。
   - `catalogInputKeys`：来自 `ComponentService.getComponentForElement` 的 `inputParameters[].key`；无组件时 catalog 为空，模型中已有 `inputParameter` 均视为自定义行。
   - `properties-panel`：在 `isInputTab(tab)` 且 `element.type === 'bpmn:CallActivity'`（或 ServiceTask 若需对称，**本变更仅 Call Activity**）时渲染「自定义输入」折叠区。
   - 有 `componentId` 时仍展示组件契约「输入」分组 + 自定义输入区（过滤声明 key）。

5. **Propagate all variables 默认**
   - 在 `base-pallete-provider.initElement`（Call Activity 从调色板创建）及必要时在 `ComponentService.initElement` 之后对 Call Activity 调用 `elementModel.ensurePropagateAllVariables`（Camunda 实现已存在）。
   - 不新增属性面板开关；默认即开启，与 spec「默认为 true」一致。

6. **输入 Tab 内容（无 componentId）**
   - `ComponentPropertyProvider` 在无组件时不生成目录「输入」分组（或仅空 Tab 壳）；用户仅通过「自定义输入」维护 `inputParameter`。
   - **不**为 process-pick 路径拉取 as-component。

## Risks / Trade-offs

- **[Risk] 仅存 processId 无 calledElement** → 部署后引擎不知子流程定义；接受为阶段性，后续 change 再接 `calledElement`。
- **[Risk] inputParameter 与 camunda:In 逐变量映射语义并存** → 本变更仅 InputOutput inputParameter；propagate all 覆盖大部分场景；文档说明自定义输入为 Camunda 扩展参数，非 In 的 target/source 行。
- **[Trade-off] 仅 Call Activity 自定义输入** → ServiceTask 已有目录输入 + 无对称面板；避免范围膨胀。

## Migration Plan

- 已有裸 Call Activity：补选流程、在自定义输入区维护参数；打开图时对缺少 propagate all 的元素可在首次选中时补写（可选，实现时评估是否仅新建默认）。
- 回滚：移除 process-selector 与 custom-inputs-panel 接线。

## Open Questions

- 打开旧图时是否批量补 `ensurePropagateAllVariables`：建议**仅新建**默认，旧图保持原样，除非产品要求迁移。
