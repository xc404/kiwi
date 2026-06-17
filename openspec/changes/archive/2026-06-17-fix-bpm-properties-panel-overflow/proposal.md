## Why

在 BPM 设计器中，`bpm-properties-panel` 字段较多时会出现内容溢出和可用区域被挤压的问题，导致用户无法顺畅查看和编辑全部属性。该问题直接影响流程建模效率，需要尽快修复以保证复杂流程配置可用性。

## What Changes

- 为设计器右侧属性面板提供稳定的滚动容器，确保内容过多时可以完整浏览。
- 调整编辑器布局高度与溢出策略，避免属性面板高度计算异常导致的显示错位或裁切。
- 明确属性面板与画布区域的空间分配规则，防止单侧内容增长破坏整体布局。

## Capabilities

### New Capabilities
- `bpm-properties-panel-overflow-handling`: 规范 BPM 设计器属性面板在长内容场景下的显示、滚动与布局行为。

### Modified Capabilities
无。

## Impact

- 前端页面：`kiwi-admin/frontend/src/app/pages/bpm/design/editor/bpm-editor.html`
- 相关样式：BPM 设计器页面样式（新增或调整对应 less/scss）
- 无后端 API 变更，无新增第三方依赖
