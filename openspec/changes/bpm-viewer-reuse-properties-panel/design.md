## Context

- **编辑器**：`BpmEditor` 使用 `nz-layout`，左侧 palette，中间画布 + toolbar，右侧 `nz-sider`（约 500px）内嵌可滚动区域包裹 `bpm-properties-panel`；`BpmPropertiesPanel` 依赖 `BpmnModeler`，通过 `selection.changed` 驱动 `bpm-panel-header` 与基于 `PropertyProvider` 的 tab/collapse。
- **实例查看**：`BpmViewer` 使用 `NavigatedViewer`（只读），工具栏为实例元信息条；右侧当前为 `aside` + `bpm-panel-header` + `bpm-instance-properties`（运行时变量表格），与编辑器侧栏 DOM/CSS 结构不同。

## Goals / Non-Goals

**Goals:**

- 实例查看页主区域 + 右侧栏在**视觉与结构**上与 `BpmEditor` 对齐（`nz-layout` / `nz-sider` / 滚动容器类名与间距可复用或映射到同一套 token）。
- 右侧以 **`bpm-properties-panel` 为唯一入口**，复用 `PanelHeader` + tabs 外壳；在实例场景下展示**选中元素上下文**与**运行时变量**（等价或优于当前 `bpm-instance-properties` 的信息量）。

**Non-Goals:**

- 不要求在实例查看页支持对 BPMN 元素的**建模写回**（与 `Modeler` 的 `modeling.updateProperties` 一致）；只读语义保持不变。
- 不要求一次性重写全部 `PropertyProvider`；可在实例模式下仅激活「运行时变量」等与查看相关的 tab，或先提供最小可用集合。

## Decisions

1. **布局**：将 `bpm-viewer.html` 中 `instance-body` 改为与 `bpm-editor` 同构的三栏骨架（无 palette 则左侧省略或使用单列 `nz-layout`：中间画布 + 右侧 `nz-sider`），右侧 sider 宽度与编辑器一致（如 500px）并包裹 `bpm-editor-properties-scroll` 同类滚动层，便于样式复用。
2. **复用 `BpmPropertiesPanel` 的方式（择一，实现阶段选定）**：
   - **方案 A（推荐评估）**：扩展 `BpmPropertiesPanel` 的输入：除 `bpmnModeler` 外，支持可选的 `viewer: NavigatedViewer` 与 `runtimeContext`（如 `variables`、`selectionIsRoot` 信号）。当 `viewer` 存在时，不订阅 Modeler 独有 API，仍订阅 `selection.changed`（两 viewer 事件模型一致）；增加「变量」tab 或注入只读 PropertyTab，数据来源 `ProcessInstanceService` 已有过滤逻辑。
   - **方案 B**：新增薄包装组件 `BpmPropertiesPanelViewer`，内部组合 `PanelHeader` + `nz-tabs`，仅复用样式与头图，变量区仍用 `bpm-instance-properties` 模板片段——**不满足**「复用 `bpm-properties-panel`」的强约束，仅作退路。
3. **优先采用方案 A 的子集**：先让 `BpmPropertiesPanel` 接受 `BpmnModeler | NavigatedViewer` 类型联合（或接口 `selection: { on; get }`），只读路径下隐藏/禁用依赖 `modeling` 的 `PropertyGroup` 行。

## Risks / Trade-offs

- **类型与 API 差异** → `NavigatedViewer` 与 `Modeler` 的 `get()` 集合不完全一致；需在面板内对 `modeling` 等特性做存在性判断，避免运行时错误。
- **属性 Tab 空内容** → 若现有 tab 均依赖编辑能力，实例页可能出现空 tab；缓解：实例模式仅展示「概要 + 变量」等明确可用的 tab。
- **回归** → 实例变量过滤逻辑（按 activity / 根）必须在迁移后行为一致；需对照现有 `filteredVariables()` 与 UI 测试。

## Migration Plan

1. 实现布局与面板适配后，在 `BpmViewer` 路由上手工回归：加载实例、切换选中、核对变量列表与状态标签。
2. 若 `bpm-instance-properties` 无其它引用，可删除组件及模板样式；否则保留并仅移除 `BpmViewer` 引用。

## Open Questions

- `PropertyProvider` 是否需要在实例模式下返回不同的 tab 列表（由注入 token 控制），还是在 `BpmPropertiesPanel` 内硬编码「运行时变量」区块更合适。
- 顶部是否保留 `app-page-header`，与编辑器无 header 的差异是否接受，或是否改为与 `bpm-editor-process-meta` 类似的实例 meta 条并统一全页高度计算。
