# 整合 extract 分支布局与 Chat 优化

把 `feat/extract-kiwi-bpmn-assistant` 上已落地、当前 `feat/designer-agent-harness` 缺失的两块合进来，**保留**现有左侧「组件库 / Agent」Tab 与 designer harness / `bpm-ai-chat`。

## 纳入

- `app-chat`：标题栏拖动、`attentionOpen`、`[chat-context-card]` 投影
- `bpm-editor`：flex 顶栏 + 三栏壳、侧栏折叠/拖宽/`localStorage`、窄屏自动收起、`canvas.resized`
- 流程 meta 收进顶栏（hover 详情）
- `bpm-ai-chat` 写工作流确认卡改到对话区内，去掉挡属性面板的 FAB

## 不纳入

- extract 的 XML undo 栈、Agent 预览 banner、`syncSessionFromServer`
- 改 `moddleExtensions`（保持当前 `camunda` / `kiwi`）
- 删除 `bpm-ai-chat`

## 布局

```
header: meta | toolbar | palette/properties toggles
palette(+tabs) | resizer | canvas | resizer | properties
浮层 app-chat（可拖）+ 对话内 authoring 卡
```
