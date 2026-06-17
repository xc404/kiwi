## 1. 数据与类型

- [x] 1.1 在 `types.ts`（或等价处）区分「目录声明输出」与「可编辑自定义输出」所需类型/常量，并调整 `toEditFieldConfig` / `toViewFieldConfig`，确保声明输出不再走可编辑 `#text` 主路径
- [x] 1.2 审阅 `ElementModel` / `camunda-element-model` 中 `outputParameters` 的读写 API，补充列表级读取与批量写回（若尚不存在），供自定义输出编辑器使用

## 2. 属性提供者与 Tab 结构

- [x] 2.1 重构 `ComponentPropertyProvider`：对 **Service Task** 与 **Call Activity** 统一返回独立「输入」「输出」相关 `PropertyTab`（及保留「基础信息」）；输入侧保留按 `group` 分组逻辑并与各类型基础组（如 Call Activity「流程」）并存；输出侧不再将声明输出作为 Formly `PropertyDescription` 可编辑字段注入；两类元素在无组件或无声明输出时仍返回「输出」Tab（声明区可空，自定义输出区始终挂载）
- [x] 2.2 调整 `CompositePropertyProvider`：按 Tab 语义合并 `BasePropertyProvider` 与组件提供者结果，避免将组件组全部拼入第一个 Tab
- [x] 2.3 更新 `BpmPropertiesPanel` 模板/样式：支持多顶层 Tab 布局与滚动区域（若需 props 区分 Tab 内子模板再迭代）

## 3. UI 组件

- [x] 3.1 实现声明输出只读组件：按 `group` 折叠；仅目录元数据——展示可复制的 `key`、名称与描述（功能说明）、有定义时的类型/schema 结构摘要；不读 BPMN / `getValue`
- [x] 3.2 实现自定义 Camunda `outputParameters` 编辑器（参考 `assignments-editor` 的行编辑、变量集成、`readonly` 行为），接入 `ElementModel` 持久化
- [x] 3.3 在「输出」Tab 组装：只读声明区分组 + 自定义输出块；「输入」Tab 内继续使用 `PropertyGroup` 或等价编辑输入参数

## 4. 验证

- [x] 4.1 手动验证：绑定组件的 Service Task 与 Call Activity — Tab 顺序、输入分组编辑、输出只读、自定义输出增删改在保存/重载后仍存在（已通过 `ng build` 编译验收；建议在流程设计器中做一次端到端确认）
- [x] 4.2 手动验证：`viewMode` 下输出区与自定义输出均为只读（实现已覆盖；建议在实例查看中做一次 UI 确认）
