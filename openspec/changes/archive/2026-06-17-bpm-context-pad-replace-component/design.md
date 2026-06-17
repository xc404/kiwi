## Context

BPM 设计器通过 `append-component-module` 在 context-pad 提供「追加业务组件」：弹层列出 `ComponentProvider` 分组与 `getRecentComponentUsages`，选中后由 `BpmEditorAppendService.appendComponentFromContextPad` 创建新 `ServiceTask` 并 `ComponentService.initElement` 写入 `componentId` 与默认输入。

已存在节点上的参数分布在：

- **标准输入**：`ElementModel` / `FlowableElementModel` 的 `inputParameter`（`camunda:InputParameter`）
- **目录输出**：组件元数据 `outputParameters`（属性面板只读区）
- **自定义输出**：`custom-outputs-panel` 维护的、**不在**当前组件 catalog 中的 `outputParameter`

用户需要在不删节点、不改连线的前提下替换 `componentId`，并按 spec 合并参数。

## Goals / Non-Goals

**Goals:**

- 为已绑定组件的 `ServiceTask` 增加 context-pad「替换组件」与 popup（`kiwi-replace-component`）。
- 实现 `replaceComponent(element, targetComponent)`：原地更新绑定 + 参数合并（保留自定义输出、交集输入、清理旧 catalog 输出）。
- 与 append 共用组件列表数据源；操作可 undo。

**Non-Goals:**

- CallActivity、子流程、网关等非 ServiceTask 替换。
- 后端 API、组件库元数据 schema 变更。
- 自动修复下游 SpEL/JUEL 中对旧 catalog 输出 key 的引用。
- AI 或工具栏触发替换（可后续复用 service）。

## Decisions

### 1. 独立 module + config，镜像 append 结构

**选择**：新增 `replace-component-module.ts`（popup + context-pad provider）与 `kiwiReplaceComponent` config（`getComponentGroups`、`getRecentUsages`、`replace`），在 `bpm-editor.ts` 注册。

**理由**：与 `append-component-module.ts` 对称，避免 append popup 条目混用导致误触「追加」逻辑；context-pad entry id 为 `replace-component`。

**备选**：扩展现有 `append-component-module` — 拒绝，职责混杂且 `canAppend` / `canReplace` 条件不同。

### 2. `BpmEditorReplaceService` 承载合并逻辑

**选择**：新建 `bpm-editor-replace.service.ts`，`init(modeler)` 后暴露 `replaceComponentFromContextPad(element, component)`。

**步骤（单次 `modeling` 命令或 `commandStack` 可撤销批次）**：

1. 读取 `oldComponent = componentService.getComponentForElement(element)`；若无则 abort。
2. 快照：
   - `customOutputs`：`elementModel.getOutputParameters` 中 name ∉ `oldComponent.outputParameters` 的 name→value
   - `inputValues`：对 **新** 组件每个 `inputParameters[].key`，若 `elementModel.getValue(..., inputParameter, key)` 非空则记录
3. 更新绑定：`componentService.setComponentId` + 将 `flowable:delegateExpression` / 名称等按 `initElement` 规则设置（可抽取 `applyComponentDefaults(modeler, element, item, { onlyInputs: true })`）。
4. 对新组件每个 input：若 `inputValues` 有 key 用旧值，否则 `defaultValue`。
5. 删除旧 catalog 输出：对 `oldComponent.outputParameters` 中每个 key，若不在 `newComponent.outputParameters`，调用 `elementModel.removeOutputParameter`（**不**删除 customOutputs 快照中的 key）。
6. 写回 customOutputs 快照。
7. 刷新属性面板选中（`selection` 不变）。

**理由**：合并规则跨 `ElementModel` / `ComponentService`，不宜塞进 diagram-js provider；便于单测与 AI 复用。

### 3. 显示条件：`canReplaceComponent(element)`

```text
bpmn:ServiceTask && componentId 存在 && 非 label && 组件库非空
```

与 append 的 `canAppendComponent`（任意 FlowNode）区分；不在 EndEvent 等显示 replace。

### 4. Popup 条目

复用 append 的「最近使用 + 分组」构建逻辑；可抽 `buildComponentPopupEntries(kiwi, element, action)` 到共享 util，replace 传入 `kiwi.replace` 回调。

**排除当前组件**：可选在 replace 菜单中隐藏与当前 `componentId` 相同的项，避免无操作替换（实现简单，建议在 tasks 中做）。

### 5. `ComponentService` 小扩展

新增 `replaceElementComponent(modeler, element, component)` 或让 replace service 直接编排，避免 `initElement` 全量覆盖输入（`initElement` 目前对所有 input 写 default，会冲掉保留逻辑）。

**选择**：replace service 显式合并，**不**直接调用 `initElement`；仅复用 `setComponentId` 与 input 写入 API。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| 下游表达式仍引用旧 catalog 输出 | 文档与属性面板提示；不自动改写 SpEL |
| Flowable moddle 与 Camunda 字段不一致 | 沿用现有 `FlowableElementModel.setValue` 路径 |
| undo 粒度不当导致半状态 | 使用 `modeling.updateModdleProperties` 批量或自定义 command |
| 与 append 重复代码 | 抽 shared popup entry builder（可选，非阻塞首版） |

## Migration Plan

纯前端功能；合并后刷新设计器即可。无数据迁移。回滚：移除 module 与 service 注册。

## Open Questions

- 替换时是否更新节点 `name` 为新组件名：建议 **保留** 用户已改名称，仅当 name 等于旧组件默认名时同步为新组件名（可选，tasks 标为 nice-to-have）。
- 图标 className：context-pad 使用 `bpmn-icon-screw-wrench` 或复用 `bpmn-icon-service-task` 以与 append 区分。
