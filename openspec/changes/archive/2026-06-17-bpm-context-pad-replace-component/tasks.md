## 1. Replace 服务与参数合并

- [x] 1.1 新增 `bpm-editor-replace.service.ts`：`init(modeler)`、`replaceComponentFromContextPad(element, component)`
- [x] 1.2 实现替换前快照：自定义输出（非旧 catalog output 的 outputParameter）、新组件 input 交集的已配置值
- [x] 1.3 实现替换应用：`setComponentId`、按新组件写入 input（交集保留 / 新 key 用 default）、删除旧 catalog 独有 output、写回自定义输出
- [x] 1.4 确保操作可通过 bpmn-js commandStack undo/redo

## 2. Context-pad 与弹层模块

- [x] 2.1 新增 `replace-component-module.ts`：`KiwiReplaceComponentPopupProvider`（`kiwi-replace-component`）、`KiwiReplaceComponentContextPadProvider`
- [x] 2.2 实现 `canReplaceComponent`（ServiceTask + 已绑定 componentId + 组件库非空）
- [x] 2.3 弹层复用「最近使用 + 分组」列表；选中调用 `config.kiwiReplaceComponent.replace`；可选隐藏当前 componentId
- [x] 2.4 context-pad 注册 `replace-component` 入口（标题「替换组件」，图标与 append 区分）

## 3. 编辑器集成

- [x] 3.1 在 `bpm-editor.ts` 注册 `replaceComponentModule` 与 `kiwiReplaceComponent` config（对接 `BpmEditorReplaceService`）
- [x] 3.2 提供 `BpmEditorReplaceService` 为 editor provider（与 `BpmEditorAppendService` 并列）

## 4. 验证

- [x] 4.1 手动验证：替换后连线与节点 id 不变、自定义输出保留、同名 input 保留、旧 catalog output 清除、undo 恢复
- [x] 4.2 验证无 componentId 的 ServiceTask、CallActivity、网关不显示替换入口
