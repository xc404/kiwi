## 1. 定位与样式覆盖

- [x] 1.1 在浏览器 DevTools 中确认 `bpmn-js` 注入画布根节点类名（如 `.djs-container`）及产生边框的 CSS 属性
- [x] 1.2 在 `bpm-editor.scss` 中于 `.canvas`（或 `:host`）下增加针对性覆盖，去除容器边框/outline，必要时处理 `box-shadow`

## 2. 回归

- [x] 2.1 本地打开设计器，确认画布外框无库默认边框，图元与连线显示正常
- [x] 2.2 确认属性面板、工具栏布局与滚动行为未回归
