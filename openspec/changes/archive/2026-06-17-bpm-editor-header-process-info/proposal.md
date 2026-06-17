## Why

流程设计页（`bpm-editor`）当前仅在工具栏区域提供操作，缺少对当前流程定义关键元信息的集中展示。编辑时用户难以快速确认流程名称、最近保存时间、部署时间及版本状态。在页面顶部增加一块只读的流程信息区，可提升可感知性与操作上下文，减少误操作。

## What Changes

- 在 `bpm-editor.html` 画布区域上方（工具栏之上或与工具栏同一顶栏内，以实现设计为准）增加「流程信息」展示条。
- 展示字段至少包括：**流程名称**、**最后修改时间**（对应后端实体上的更新时间）、**部署时间**；可选补充 **流程 ID**、**项目 ID**、**草稿版本号 / 已部署版本号** 等（以接口 `GET /bpm/process/{id}` 实际返回字段为准）。
- 数据来源于已加载的 `bpmProcess`（`loadDefinition` / 保存与部署成功后的刷新），加载中或数据未就绪时显示占位或骨架，避免空白误导。
- 不新增后端接口；若前端现有字段名与接口 JSON 不一致（例如 `updatedAt` vs `updatedTime`），在实现阶段对齐或做轻量映射。

## Capabilities

### New Capabilities

- `bpm-editor-process-info-header`: 定义 BPM 设计器顶部流程元信息展示的内容、可见性与与数据同步行为。

### Modified Capabilities

无。

## Impact

- 前端：`kiwi-admin/frontend/src/app/pages/bpm/design/editor/bpm-editor.html`、`bpm-editor.ts`、`bpm-editor.scss`；可选用 ng-zorro 的 `nz-descriptions` / `nz-tag` / 文本行等，与现有页面风格一致。
- 后端：无变更（沿用现有流程定义查询接口）。
- 无新增第三方依赖（优先使用项目已有 UI 组件）。
