## ADDED Requirements



### Requirement: 通用只读属性行组件标识



系统 SHALL 提供名为 **`bpm-readonly-property-row`** 的 Angular 组件，用于在 BPM 设计器相关界面中展示单条只读属性：不 SHALL 在组件公开 API 或必选文案中绑定「自定义输出」「Output」等业务专有术语；业务含义由父级调用场景决定。



#### Scenario: 组件可被非输出场景引用



- **WHEN** 某只读属性面板需要「名称 + 主值 + 配置/运行时对照」且能提供 `propertyDescription` 与 `variables`

- **THEN** 该面板 SHALL 能使用 `bpm-readonly-property-row` 而不必位于自定义输出功能内



### Requirement: 行布局与主展示值



组件 SHALL 在单行内展示：**左侧** 为属性名称（或调用方通过 `propertyDescription` 提供的展示用标识），**右侧** 为主展示值。主展示值 MUST 能结合该行 **设计时配置文本** 与 **运行时 `variables`** 中按约定键解析得到的值进行呈现；呈现规则对「自定义输出」首接场景 SHALL 与迁移前 `CustomOutputsPanel` 只读路径对用户可见的合并语义一致或可解释地等价。



#### Scenario: 名称与主值可见



- **WHEN** 传入非空属性标识的配置且已传入 `variables`（可为空数组）

- **THEN** 用户 SHALL 能在该行左侧看到名称（或约定标识），在右侧看到主展示值



### Requirement: 运行时变量输入



组件 MUST 接受当前 BPM 节点（或等价上下文）的 **运行时变量集合** 作为输入，并 MUST 能按 **`PropertyDescription.key`** 与运行时变量项的 `name` 匹配，解析运行时取值用于主展示与详情。



#### Scenario: 变量集合绑定



- **WHEN** 父级将 `variables` 绑定到该组件

- **THEN** 组件 SHALL 使用该集合解析运行时值；若无匹配项则主展示与详情中的运行时值使用统一空占位策略（如「—」或空字符串，由实现一致化）



### Requirement: 详情提示内容



组件 SHALL 在行尾提供可点击的提示入口。用户激活该入口后，系统 SHALL 展示包含以下信息的详情：**属性名称（或与配置键对应的展示标签）**、**设计时配置值**、**运行时实际值**（自 `variables` 解析）。



#### Scenario: 点击提示查看三项对照



- **WHEN** 用户点击该行的提示控件

- **THEN** 用户 SHALL 能看到名称、配置值与运行时实际值的对照信息



### Requirement: 最小对外契约



组件 SHALL 以 **`PropertyDescription`**（来自 `property-panel/types.ts`）与 **`variables`** 为最小对外契约：`PropertyDescription` MUST 包含 **`key`**（用于与运行时变量匹配）；设计时配置文本由 **`valueText`** 或 **`defaultValue`**（字符串化）提供，二者至少其一可被调用方填充。禁止另行定义与 `PropertyDescription` 并行的只读专用类型。`variables` MUST 为运行时变量列表。调用方 SHALL 能在不依赖 `CustomOutputsPanel` 内部实现的情况下完成集成。



#### Scenario: 契约稳定的复用



- **WHEN** 任意调用方传入符合上述契约的对象与 `variables` 数组

- **THEN** 组件 SHALL 能独立完成布局、主值与详情展示



### Requirement: 自定义输出只读路径接入



在 BPM 设计器「自定义输出」区域处于只读模式时，系统 SHALL 使用 **`bpm-readonly-property-row`** 渲染每一条有效（名称非空白）的行，替代仅在父级模板内联列表项的写法。



#### Scenario: 多条自定义输出



- **WHEN** 只读模式下存在多条有效的自定义属性行

- **THEN** 每一行 SHALL 对应一个 `bpm-readonly-property-row` 实例并传入对应行描述与当前节点 `variables`


