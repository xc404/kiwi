# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/frontend_intermediate_catch_event_82ba8a18.plan.md`；**已实现**后归档，plan 文件已删除。

## 实现路径（以代码为准）

- `PaletteItem.eventDefinitionType` + `pallete.ts` 创建时挂载 `eventDefinitions`
- `BasePaletteProvider`：中间事件三分组 + `ReceiveTask`；官方 `bpmn-icon-*` 图标
- `pallete.html`：`<span [class]="paletteItem.icon">` 渲染
- `BasePropertyProvider`：「事件配置」组（messageName / signalName / timerType+timerValue）
- `ElementModel`：`messageName` / `signalName` / timer 读写 + rootElements 复用 Message/Signal

## 与 plan 的差异

- **`legacyIconAlias`**：plan 建议对 DB 历史数字图标做映射；当前 `component-service` 仅用默认官方 icon 回退，未单独实现 alias 函数
- **手动验收**：plan 中 cyro-et 轮询骨架等需在编辑器内自行回归

## 非目标（仍未做）

- EventBasedGateway palette 条目
- 消息/信号下拉选择 UI（仍为文本输入）

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
