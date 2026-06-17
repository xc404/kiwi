## Context



`CustomOutputsPanel` 在只读模式下仍用内联 `<ul>` 展示；同时产品期望同一套「配置 vs 运行时」行 UI 可出现在 **任意只读属性行**，而非仅限自定义输出。过早定名 `bpm-custom-output-readonly-row` 会误导调用方并阻碍放在共享目录。



约束：不改变模型写入协议；`variables` 仍由流程查看/跟踪上下文注入。



## Goals / Non-Goals



**Goals:**



- 提供领域无关的只读行组件 **`bpm-readonly-property-row`**：输入 **`propertyDescription`**（至少含用于展示与变量匹配的字段）与 **`variables`**。

- 行内：左 **标识/名称**，右 **主展示值**，尾 **提示**；浮层三项：**名称（或标签）**、**设计时配置文本**、**运行时值**（按约定键在 `variables` 中解析）。

- `CustomOutputsPanel` 只读分支使用该组件；组件文件放在 **共享位置**，便于属性面板其他区域日后引用。



**Non-Goals:**



- 不包揽所有属性类型的专用格式化（如日期/枚举）；首版以字符串级展示与变量对照为主，调用方可预处理 `valueText`。

- 不新增后端接口。



## Decisions



1. **组件与选择器**  

   Standalone 组件，选择器 **`bpm-readonly-property-row`**（不再使用 `*-custom-output-readonly-*` 前缀）。



2. **目录位置**  

   实现时优先放在 **`kiwi-admin/frontend/src/app/pages/bpm/design/property-panel/`** 下独立子目录（例如 `readonly-property-row/`），或项目已有的 `shared` 组件区——**不要**仅放在 `custom-outputs/` 内，以免暗示仅限输出。具体路径以实现时目录惯例为准，以「属性面板兄弟模块可一行 import」为目标。



3. **输入模型：`PropertyDescription`**  

   直接使用 **`property-panel/types.ts`** 中的 **`PropertyDescription`**，不另设 `ReadonlyPropertyDescription`。  

   - **`key`**（必填）：与运行时 `variables` 项的 `name` 匹配。  

   - **`name`**（可选）：展示用标签；缺省时用 `key`。  

   - **配置文本**：优先 **`valueText`**（建议在类型上作为可选字段补充）；若无则回退 **`defaultValue`** 字符串化。自定义输出接入时将模型中的配置写入 `valueText`。



4. **`variables`**  

   与现面板一致：项含 `name`、`value`；子组件用 **`PropertyDescription.key`** 与变量项 `name` 匹配。



5. **主行与详情**  

   与 `CustomOutputsPanel.readOutputValue` 对齐的合并规则可作为 **自定义输出接入时的默认行为**（由组件内同一套推导函数实现，或可选 `@Input() mergeStrategy` 留待后续）；详情浮层固定三项：名称、配置值、运行时值。



6. **提示交互**  

   沿用 `nz-popover` + 信息图标、点击触发。



7. **语义**  

   组件 **不得** 在模板或文档中写死「输出」「OutputParameter」；调用方负责业务语义。



## Risks / Trade-offs



- **[Risk] 泛化后不同属性类型的展示差异** → 首版统一字符串流；特殊格式由各父组件预处理 `valueText` 或后续扩展可选输入。  

- **[Risk] 与旧内联字符串不一致** → 自定义输出路径做单场景对照测试。



## Migration Plan



无数据迁移。若曾生成旧组件文件名，实现阶段统一为 `readonly-property-row.*` / 选择器 `bpm-readonly-property-row`。



## Open Questions



- 是否在第二迭代增加可选 **`label`**，用于左侧展示友好名而 **`name`** 专用于变量键：需求未强制，规格保留扩展空间。


