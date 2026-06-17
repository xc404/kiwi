## Context

`bpm-editor` 通过 `BpmnModeler` 将编辑器挂载到容器选择器 `.canvas`（见 `bpm-editor.ts`）。`bpmn-js` 依赖的 `diagram-js` 默认样式（`diagram-js.css`）会为画布根容器（如 `.djs-container` 等类名，随版本以实际 DOM 为准）设置边框或轮廓，从而在页面上呈现“画布边框”。本变更仅处理该视觉层，不触碰业务逻辑。

## Goals / Non-Goals

**Goals:**

- 在编辑器视图中，用户不应再看到由 bpmn-js/diagram-js 默认样式带来的画布外框边框。
- 样式覆盖限定在 BPM 设计器编辑器作用域内，避免影响其他页面或全局。

**Non-Goals:**

- 不修改 BPMN 图形元素本身描边（节点、连线、泳道等）。
- 不替换或 fork 上游 `diagram-js.css` 整文件。

## Decisions

1. **作用域覆盖**  
   在 `bpm-editor` 组件样式中，使用 `:host` 或 `.canvas` 后代选择器，针对 `diagram-js` 画布根节点（如 `.djs-container` 等，以实现时 DevTools 与当前 `bpmn-js` 版本为准）设置 `border: none`、`outline: none`（或等价），必要时处理 `box-shadow` 若表现为边框。  
   若需穿透 Angular 封装，使用 `::ng-deep` 仅作用于 `.canvas` 子树，避免全局污染。

2. **不改动 TS 挂载方式**  
   保持 `container: ".canvas"` 不变，避免引入额外 wrapper 或 API 变更。

3. **与属性面板区分**  
   右侧属性栏 `border-left` 等布局分隔线属于应用壳层，本变更不删除，除非与“画布边框”视觉上混淆（若混淆，可仅微调颜色/透明度，不纳入本变更最小范围）。

## Risks / Trade-offs

- 上游 `diagram-js` 升级可能调整类名或增加边框样式 → 回归时关注升级说明；选择器尽量绑定在稳定的 `.canvas` 子树。
- 若存在双层边框（应用 + 库），需确认只移除库层，避免误删其他 UI 边框。

## Migration Plan

前端部署即可；无需数据迁移。

## Open Questions

无。
