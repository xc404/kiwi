## Why

BPMN 设计器无法从 palette 拖出带正确事件定义的中间捕获事件与接收任务；属性面板也无法配置 messageRef / timer / signalRef，阻碍消息关联、定时轮询等流程建模。

## What Changes

- Palette 支持中间消息/定时/信号捕获事件 + 接收任务，创建时自动挂载 `eventDefinitionType`
- 属性面板「事件配置」：messageName、signalName、timerType、timerValue
- `ElementModel` 读写 message/signal rootElements 与 timer 子元素
- Palette 图标统一迁移为 bpmn-js 官方 CSS class

## Capabilities

### New Capabilities

- （无 main spec；前端设计器能力。）

### Modified Capabilities

- （无。）

## Impact

- `palette/`、`property-panel/base-property-provider.ts`、`extension/element-model.ts`
- `component-service.ts`、`component-provider.ts` 默认 icon

## 非目标

- EventBasedGateway
- 消息抛出/启动/边界/错误事件
- Message/Signal 下拉选择 UI
