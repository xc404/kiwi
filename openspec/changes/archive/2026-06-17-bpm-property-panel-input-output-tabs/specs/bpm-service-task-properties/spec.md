## ADDED Requirements

### Requirement: Service Task 与 Call Activity 属性面板区分输入与输出 Tab

当选中元素为 `bpmn:ServiceTask` 或 `bpmn:CallActivity` 时，属性面板 SHALL 在组件相关区域提供至少两个顶层 Tab：**输入**与**输出**（文案可为中英文产品约定，但语义固定）。**输出** Tab SHALL 始终展示，不得因无组件元数据、或组件元数据中无声明输出（`outputParameters` 为空或缺失）而隐藏；无声明输出可展示时，声明输出只读区 SHALL 允许为空状态。**输入** Tab：在已解析到组件元数据时，SHALL 按组件参数上的 `group` 字段折叠分组展示所有输入参数并保持可编辑（与变更前输入参数行为等价或增强）；无组件元数据时输入区 SHALL 仍可包含与该元素类型相关的基础组（例如 Call Activity 的「流程」绑定、Service Task 的「组件类型」等），且 SHALL NOT 因缺少组件而崩溃。**输出** Tab SHALL 单独承载输出相关内容（声明只读区 + 自定义输出区），不得将输入与输出混在同一 Tab 的同一折叠组内无标签区分。

#### Scenario: 有组件元数据时可见输入与输出 Tab

- **WHEN** 用户选中已绑定组件的 Service Task 或 Call Activity 并打开属性面板
- **THEN** 面板展示可切换的「输入」与「输出」Tab，且「输入」Tab 内分组与组件 `inputParameters` 的 `group` 一致（并与该元素类型下既有基础组并存）

#### Scenario: 无组件元数据仍保留输出 Tab

- **WHEN** 用户选中未绑定或无法解析组件的 Service Task 或 Call Activity 并打开属性面板
- **THEN** 「输出」Tab SHALL 仍展示；声明输出只读区可为空；「自定义输出」编辑区 SHALL 在非 view 模式下可用，以便用户手动添加 Camunda `outputParameters`

#### Scenario: 无声明输出仍保留输出 Tab

- **WHEN** 用户选中已绑定组件但其元数据中无声明输出或 `outputParameters` 列表为空
- **THEN** 「输出」Tab SHALL 仍展示；声明输出只读区可为空；「自定义输出」编辑区 SHALL 在非 view 模式下仍可用

### Requirement: 组件声明的输出为只读说明与结构

对于来自组件目录的 `outputParameters`（即注解 / `BpmComponent` 中的输出契约），在 Service Task 与 Call Activity 上属性面板 SHALL 仅以只读形式展示其业务含义与数据结构相关信息；数据源 SHALL 仅为组件目录元数据，SHALL NOT 从 BPMN 元素或 `elementModel.getValue` 等读取声明项的绑定值。展示 SHALL 包含：`key`（以易于选中复制的方式呈现）、展示名称与描述（功能说明）；若元数据包含类型或 schema 信息则 SHALL 一并展示结构。用户 SHALL NOT 通过声明输出区域的主交互修改绑定到 BPMN 的字段（不得使用与输入参数等价的可编辑表达式主控件）。

#### Scenario: 声明输出不触发写回

- **WHEN** 用户在「输出」Tab 仅浏览组件声明的输出分组
- **THEN** 界面不提供可提交编辑的表单控件来改变这些输出的 BPMN 绑定作为主路径

#### Scenario: 展示 key 与结构信息

- **WHEN** 用户在「输出」Tab 浏览组件声明的输出
- **THEN** 只读区 SHALL 展示该项的 `key` 以便复制，并展示名称与描述；若元数据中带有类型或 schema 类信息，则 SHALL 向用户展示该结构信息以便理解变量形态

### Requirement: 自定义 Camunda 输出映射编辑

在「输出」Tab 中，除声明输出只读区外，系统 SHALL 提供「自定义输出」编辑区，允许用户以列表形式新增、修改、删除行，并将其持久化为 Camunda 扩展下与**当前选中元素**（`bpmn:ServiceTask` 或 `bpmn:CallActivity`）关联的 `outputParameters`（与现有 `camunda:inputOutput` / `camunda:OutputParameter` 语义一致）。交互模式 SHALL 与现有 `assignments-editor` 的设计语言一致（行式编辑、清晰的变量/表达式编辑入口、只读模式下禁用编辑）。

#### Scenario: 添加一行自定义输出

- **WHEN** 用户在自定义输出区添加一行并填写有效映射并保存（失焦或等价提交机制）
- **THEN** 图中对应 Service Task 或 Call Activity 元素的 Camunda `outputParameters` 包含该新行定义

#### Scenario: 删除一行

- **WHEN** 用户删除自定义输出区中的某一行并保存
- **THEN** 图中不再包含该行对应的 `camunda:OutputParameter` 定义

#### Scenario: 流程实例只读查看

- **WHEN** 属性面板处于流程实例查看（view）模式
- **THEN** 自定义输出区为只读，不得修改 BPMN 模型

### Requirement: 多 Tab 与基础属性合并行为

当存在基础 BPMN/Camunda 属性 Tab（由 `BasePropertyProvider` 等提供）时，`CompositePropertyProvider` 或等价合并逻辑 SHALL 将组件相关的「输入」「输出」Tab 与基础 Tab 以清晰顺序合并，且 SHALL NOT 将「输入」「输出」组无差别追加到第一个 Tab 内而导致与「基础信息」语义混淆。

#### Scenario: 合并后输入输出仍为独立 Tab

- **WHEN** 用户打开已绑定组件的 Service Task 或 Call Activity 属性面板
- **THEN** 用户可在独立 Tab 中切换到「输入」与「输出」，而不必在同一 Tab 内滚动查找输出区域
