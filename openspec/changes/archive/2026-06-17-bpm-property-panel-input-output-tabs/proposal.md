## Why

BPM 设计器里，组件元数据中的 `outputParameters` 表示流程执行后写入上下文的**固定契约**，当前却与输入一样走可编辑表单，容易让人误以为要在面板里「填写输出值」。同时，输入与输出混在「基础信息」折叠组里，信息架构不清晰；用户还需要在 Camunda 语义下**额外声明**自定义输出（`camunda:outputParameters`），与已有 assignments 类编辑体验对齐。

## What Changes

- **输出元数据只读展示**：由 `ComponentDescription` / `BpmComponent` 扫描得到的输出项，在属性面板中仅展示目录侧信息——`key`（便于复制）、功能说明（名称与描述）、有定义时的类型/schema 结构；不读取 BPMN 中声明项绑定值，不再作为可编辑字段写回模型。
- **页签拆分**：属性面板中，与组件参数相关的区域拆为两个顶层 Tab：**输入**（仍按 `group` 折叠分组，可编辑表达式等）与**输出**（只读分组 + 自定义输出区）。对 **Service Task** 与 **Call Activity** 采用相同规则：**输出** Tab 在无组件元数据或无声明输出时仍保留，以便仅通过自定义输出编辑 Camunda `outputParameters`。
- **自定义输出编辑器**：在「输出」Tab 增加一块与 `assignments-editor` 类似的列表式编辑，读写 Camunda 的 `outputParameters`（目标变量名 + 来源表达式等），与组件扫描得到的输出分组并存。
- **提供者合并策略**：调整 `CompositePropertyProvider` / `ComponentPropertyProvider` 与 `BpmPropertiesPanel` 的 Tab 结构，使「基础信息」等与 I/O 分离，避免把所有组塞进第一个 Tab。

## Capabilities

### New Capabilities

- `bpm-service-task-properties`: BPM 设计器中 **Service Task** 与 **Call Activity** 属性面板的输入/输出分区、输出元数据只读展示，以及 Camunda 自定义 `outputParameters` 的编辑与持久化行为（两种元素行为一致）。

### Modified Capabilities

- （无）仓库 `openspec/specs/` 下尚无与本功能对应的既有能力规格，本次以新能力规格描述为准。

## Impact

- **前端**：`kiwi-admin/frontend` — `properties-panel`（模板/样式）、`property-provider` / `CompositePropertyProvider`、`component-property-provider`（**Service Task** 与 **Call Activity** 同源逻辑）、`property-panel/types`（`toEditFieldConfig` 等对 `outputParameter` 的处理）、可能新增只读展示组件与「自定义输出」组件；`camunda-element-model`（或等价扩展）对两类元素读写 `inputOutput` / `outputParameters`；可参考 `assignments-editor` 的 UX 与工具函数。
- **后端 / 注解**：`ComponentDescription`、`ComponentParameter`、`BpmComponent` 模型本身可不变；若前端需要更丰富的只读结构展示，可评估是否扩展 DTO 或沿用现有 schema 字段。
- **无 BREAKING** 目标：已有流程文件应仍能打开；行为变化为面板交互与输出项编辑性，不删除 Camunda 扩展能力。
