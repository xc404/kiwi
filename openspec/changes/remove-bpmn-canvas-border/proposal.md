## Why

BPM 设计器画布区域在引入 `bpmn-js` / `diagram-js` 默认样式后，会出现可见的容器边框（或等效描边），与当前管理端扁平、无边框的编辑区视觉不一致，也显得画布像被“框住”。需要在不影响建模与交互的前提下去掉该边框。

## What Changes

- 在设计器画布挂载点（`bpm-editor` 中与 `.canvas` 容器相关的样式）内，覆盖 `diagram-js` / `bpmn-js` 产生的画布容器边框或 outline，使画布区域视觉上无边框。
- 保持缩放、拖拽、选择等既有行为不变；不修改 BPMN 模型或后端接口。

## Capabilities

### New Capabilities

- `bpmn-editor-canvas-appearance`: 定义 BPM 设计器编辑器中画布区域（bpmn-js 注入的 DOM）的外观要求，包括去除默认画布边框。

### Modified Capabilities

无。

## Impact

- 前端：`kiwi-admin/frontend/src/app/pages/bpm/design/editor/bpm-editor.scss`（及必要时同组件内对 `diagram-js` 根节点的样式选择器）
- 依赖：沿用现有 `bpmn-js`，不升级版本
- 无 API / 后端变更
