## Why

流程实例查看页（`BpmViewer`）右侧属性区目前使用独立的 `bpm-instance-properties` + `bpm-panel-header`，与流程设计页（`BpmEditor`）的 `bpm-properties-panel` 布局与交互分裂，样式与结构不一致，后续维护两套面板成本高。需要对齐编辑器布局，并在右侧复用属性面板体系，使用户在「设计」与「实例查看」间有一致的侧栏体验。

## What Changes

- 将 `bpm-viewer` 页面整体布局（含 `nz-layout`）向 `bpm-editor` 靠拢：主内容区 + 右侧固定宽度属性侧栏（如 `nz-sider` + 可滚动容器），与编辑器中 `bpm-editor-properties-sider` / `bpm-editor-properties-scroll` 模式一致。
- 移除或降级仅服务于实例变量的 `bpm-instance-properties` 在 `BpmViewer` 中的直接使用；改为通过 **`bpm-properties-panel`** 提供右侧主面板（含 `bpm-panel-header` 与 tab/collapse 结构）。
- **技术前提**：`BpmPropertiesPanel` 当前依赖 `BpmnModeler` 与可编辑属性；实例页使用 `NavigatedViewer` 且需展示运行时变量。需扩展或适配层（例如：面板支持 `NavigatedViewer` / 只读模式，或注入「运行时变量」tab，与现有 `PropertyProvider` 输出并存），避免简单复制 HTML 导致逻辑分叉。

## Capabilities

### New Capabilities

- `bpm-viewer-properties-panel`：定义流程实例查看页与编辑器布局对齐、右侧复用 `bpm-properties-panel`（含只读/实例数据适配）的行为与约束。

### Modified Capabilities

- （无）与现有 `openspec/specs/` 中其它能力无需求级交叉。

## Impact

- **前端**：`kiwi-admin/frontend` 下 `bpm-viewer.ts` / `bpm-viewer.html` / `bpm-viewer.scss`；`property-panel` 下 `BpmPropertiesPanel` 及相关 provider / 模板；可能涉及 `bpm-instance-properties` 的保留（仅其它引用）或删除。
- **依赖**：`bpmn-js`/`NavigatedViewer` 与 `Modeler` 类型差异需在设计与实现中明确边界。
