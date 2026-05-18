## Why

流程设计器已支持通过上下文菜单「追加业务组件」在节点后插入新组件，但已配置的 `ServiceTask` 若需改用系统内另一组件，只能手动改属性或删建节点，易丢失连线、自定义输出与已填参数。需要在同一节点上提供与追加体验一致的「替换组件」能力，降低改型成本并保留用户已有配置。

## What Changes

- 在 BPM 设计器上下文菜单（context-pad）为已绑定业务组件的 `bpmn:ServiceTask` 增加 **替换组件** 入口，交互与「追加业务组件」一致（弹层选择组件库 / 最近使用）。
- 选中目标组件后，**原地替换**当前节点的组件绑定（`componentId` / delegate 等），不删除图形、不改变入出连线。
- **保留**：元素上用户配置的 **自定义输出**（非组件目录中的 outputParameters）；若新组件定义了与旧配置 **同名输入参数**，保留原 element 上已配置的值。
- **清除**：旧组件目录中的标准输入/输出参数中，新组件不再提供的项；新组件未覆盖的其它标准输入使用新组件默认值初始化。
- 新增 `BpmEditorReplaceService`（或扩展现有 append 服务）承载替换逻辑；新增 `replace-component-module`（或扩展现有 append 模块）注册 popup / context-pad provider。
- 不涉及后端 API 变更；不改变 CallActivity 等非 ServiceTask 节点。

## Capabilities

### New Capabilities

- `bpm-context-pad-replace-component`：BPM 设计器上下文菜单对 ServiceTask 的组件替换能力，含参数合并规则与自定义输出保留语义。

### Modified Capabilities

（无：仓库 `openspec/specs/` 中尚无 BPM 设计器相关基线 spec。）

## Impact

- **前端**：`kiwi-admin/frontend/src/app/pages/bpm/design/context-pad/`（新建或扩展 module）、`editor/bpm-editor.ts`（注入 config）、`service/`（替换业务逻辑）、`flow-elements/component-service.ts`（可能抽取参数读写辅助）。
- **依赖**：复用 `ComponentProvider`、`ComponentService`、`ElementModel`、现有 `append-component-module` 的弹层与分组模式。
- **风险**：替换后下游表达式若引用旧组件目录输出 key，需用户自行修正（与手动改组件一致）；应在设计文档中说明。
