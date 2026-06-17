## 1. 规格与 API

- [x] 1.1 在 `BpmProcessInstanceCtl` 增加 `instanceState`（`running` | `completed` | `all`），默认 `running`，映射到 `unfinished()` / `completed()` / 无附加条件
- [x] 1.2 （可选）兼容旧参数 `unfinished`：文档标注弃用；映射规则见 `design.md`

## 2. 前端

- [x] 2.1 在 `bpm-process-instances` 搜索区增加「实例状态」条件，默认运行中
- [x] 2.2 用 `instanceState` 替换或补充 `search.basicParams` 中的 `unfinished`
- [x] 2.3 列表「查看」在新标签页打开实例查看页（`window.open` + `noopener,noreferrer`）

## 3. 验证

- [x] 3.1 手工或接口测试：三种状态下结果符合预期（运行中无 `endTime` 概念于列表 DTO 可后续增强，以 Camunda 数据为准）
