# Tasks

## 1. Palette

- [x] `PaletteItem` / `getElementOptions` 增加 `eventDefinitionType`；`pallete.ts` 挂载 `eventDefinitions`
- [x] `BasePaletteProvider` 中间事件三分组 + `ReceiveTask` + 官方图标

## 2. 属性与模型

- [x] `BasePropertyProvider` 事件配置字段（messageName / signalName / timerType / timerValue）
- [x] `ElementModel` message/signal/timer getValue/setValue + rootElements 复用
- [x] messageName / signalName 纯文本输入（决策 A）

## 3. 图标

- [x] `pallete.html` 改为 `span [class]`；palette 与组件默认 icon 迁移官方名
- [ ] `legacyIconAlias` 历史 DB 数字 icon 映射（可选，未单独实现）

## 4. 验收

- [ ] 手动：拖出 4 种节点、配置、XML 往返；cyro-et 定时轮询骨架（需人工回归）
