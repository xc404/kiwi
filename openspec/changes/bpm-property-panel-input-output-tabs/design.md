## Context

- 组件元数据来自 Java `@ComponentDescription` / `@ComponentParameter` 与后端的 `BpmComponent`，其中 `outputs` 描述的是执行后写入流程变量的**契约**，在设计阶段已由组件实现确定。
- `ComponentPropertyProvider` 同时处理 `bpmn:ServiceTask` 与 `bpmn:CallActivity`（后者当前以「流程」等组为主），将 `inputParameters` / `outputParameters` 打成多个 `PropertyTab` 与按 `group` 命名的折叠组，并与 `BasePropertyProvider` 的结果在 `CompositePropertyProvider` 中合并到第一个 Tab。
- `PropertyGroup` 通过 Formly + `ElementModelProxyHandler` 读写 BPMN；`toEditFieldConfig` 对 `outputParameter` 使用 `#text` 编辑器，仍允许修改绑定到 `camunda:InputOutput` 下的 `camunda:OutputParameter`。
- Camunda 已在 `camunda-element-model` 中支持 `inputOutput`、`getOrCreateInputOutputParameter` 等。

## Goals / Non-Goals

**Goals:**

- 将**组件声明的输出**与**用户自定义的 Camunda 输出映射**在信息与交互上分离：前者只读说明 + 结构；后者可增删改，交互对齐 `assignments-editor`（行列表、变量/表达式辅助等，按实现选型）。
- 属性面板在 **`bpmn:ServiceTask`** 与 **`bpmn:CallActivity`** 上采用**相同**信息架构：清晰的 **「输入」** 与 **「输出」** 两个顶层 Tab；输入内仍按 `group` 折叠分组并保持可编辑输入体验（并与各类型既有基础组并存，如 Call Activity 的「流程」、Service Task 的「组件类型」）；输出 Tab 内为分组只读区 + 自定义输出区。**输出** Tab 对两类元素均始终存在，即使无组件解析结果或无声明输出，以便用户仅依赖自定义输出区维护 Camunda `outputParameters`。
- 与现有 `ElementModel` / Camunda moddle 扩展兼容，避免破坏已部署流程图的打开与执行语义。

**Non-Goals:**

- 不改变组件 Java 注解或 Mongo 中 `BpmComponent` 的存储模型（除非后续发现只读展示缺少必要字段且需 DTO 扩展）。
- 不在本变更中重写整个属性面板框架或非 BPMN/Camunda 引擎的通用建模。
- 不强制迁移或删除历史图中已为「声明输出」创建的 `camunda:OutputParameter` 节点（若存在）；声明输出在面板中仅以目录元数据只读展示，不以此读回 BPMN。

## Decisions

1. **数据模型：两类输出**
   - **声明输出（catalog）**：来自 `ComponentService.getComponentForElement` 的 `outputParameters` 列表，仅用于 UI 说明与结构（`key`、`name`、`description`、`type`/schema 等）。不在此区域提供「改值写回 BPMN」的主路径。
   - **自定义输出（user mappings）**：用户编辑的行集合，持久化为 `camunda:InputOutput` 下的 `camunda:OutputParameter`（与现有 Camunda 扩展一致）。与声明输出在 UI 上分块展示；若某行 `name` 与声明 `key` 冲突，在实现中采用明确策略（例如：自定义区允许任意名，执行期以 Camunda 语义为准；或校验提示重复），在实现阶段选定并在 spec 中落地为可验证行为。

2. **Tab 与提供者结构**
   - `ComponentPropertyProvider` 已对 `bpmn:ServiceTask` 与 `bpmn:CallActivity` 分支返回属性；两类型 SHALL 统一为相同的「输入」「输出」Tab 策略（Call Activity 在无组件时仍仅有「流程」等基础组 + 始终可用的「输出」Tab）。
   - 优先返回**多个** `PropertyTab`（例如 `基础信息`、`输入`、`输出`），或返回带标记的组由 `BpmPropertiesPanel` 二次分区；**推荐**由提供者直接产出「输入」「输出」Tab，减少模板魔法字符串。
   - `CompositePropertyProvider`：避免再把组件组无差别拼进 `merged[0]`；改为按 Tab 名称/约定键合并（例如同名 Tab 追加 groups，或固定顺序拼接），保证「输入 / 输出」独立成页。

3. **声明输出的呈现**
   - 不经过 `PropertyGroup` 的 Formly 双向绑定（避免误编辑）。采用独立子组件（如 `output-parameters-readonly`）在「输出」Tab 内渲染：按 `group` 折叠。内容**仅**来自组件目录元数据（`ComponentService` / `BpmComponent` 等解析结果），**不**调用 `elementModel.getValue` 或从 BPMN 读取声明项绑定。展示：`key`（便于选中复制）、展示名与描述（功能说明）、以及若元数据中定义了类型或 schema 则展示结构摘要。

4. **自定义输出编辑器**
   - 新建或复用扩展：在模式上参考 `AssignmentsEditorComponent`（`signal` 行、`valueChange`、与 `variables` 输入集成）；字段语义遵循 Camunda `OutputParameter`（`name` + `definition`/`value` 等，以当前 `camunda-element-model` 与 moddle 定义为准）。
   - 通过专用 Formly type 或直接在「输出」Tab 嵌入独立组件，由 `ElementModel` 提供列表级读写（增删行、写回 extensionElements），避免与声明输出的 PropertyDescription 混在同一 `properties` 数组。

5. **引擎抽象**
   - 若 `ElementModel` 存在 Flowable 等多实现，自定义输出区仅在 Camunda（或当前部署目标）启用；其他实现可隐藏或 TODO，避免写错命名空间。

## Risks / Trade-offs

- **[Risk] 历史图中声明输出已存为可编辑 XML** → 改为只读后，用户无法再通过同一控件修改；若仍需「改映射」，需明确是否保留次要入口（本提案 Non-Goal：主路径只读）。
- **[Risk] 声明输出与自定义输出共用 `outputParameters` 集合** → 列表合并/去重逻辑错误可能导致覆盖；缓解：代码层区分「来自 catalog 的只读块」与「用户行列表」，写回仅影响用户块或明确过滤规则。
- **[Trade-off] Tab 数量增加** → 小屏需依赖现有 `nz-tabs` 滚动/样式；可沿用 `properties-panel` 现有样式微调。

## Migration Plan

- 功能开关：可不引入；直接发布 UI 变更。
- 回滚：恢复 `ComponentPropertyProvider` / `CompositePropertyProvider` / `properties-panel` 与相关 types 的旧版行为。

## Open Questions

当前无未决项（Call Activity 与 Service Task 已对齐为同一套输入/输出 Tab 与声明只读 + 自定义输出逻辑）。
