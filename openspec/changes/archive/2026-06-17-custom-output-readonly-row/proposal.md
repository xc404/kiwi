## Why



在 BPM 属性面板的只读（查看/跟踪）场景下，需要一种统一的「左名称、右主值、尾部落详情」行级展示：既能对照 **设计时配置** 与 **运行时变量**，又能在多处复用。当前「自定义输出」仍以内联列表拼接字符串，不利于复用；且同类需求（其他只读属性项）不应绑定在「输出」专用命名或目录下。



## What Changes



- 新增 **通用** 只读属性行组件（选择器 **`bpm-readonly-property-row`**）：左侧属性标识、右侧主展示值（结合配置文本与 `variables` 解析）；最右侧提示图标，点击浮层展示 **名称**、**配置值**、**运行时实际值**。

- **自定义输出** 只读分支作为首个接入方：`CustomOutputsPanel` 只读路径改为逐行使用该组件，传入行描述与节点运行时 `variables`。

- 类型与文档上以 **`PropertyDescription`（`@Input` 名可仍为 `propertyDescription`）+ `variables`** 为契约，不另起并行类型，不隐含「仅为 output 参数」语义；后续其他只读属性面板可直接复用。



## Capabilities



### New Capabilities



- `bpm-readonly-property-row`: 定义 BPM 设计器中通用只读属性行的布局、运行时对照展示与详情交互需求（不限定于自定义输出）。



### Modified Capabilities



（无。仓库 `openspec/specs/` 下尚无对应既有规格。）



## Impact



- **前端**：新增组件及其样式；放置于 **可被多处 import 的路径**（见 `design.md`，优先属性面板公共区或 `shared`，而非仅 `custom-outputs/`）；`custom-outputs` 下面板模板与逻辑接入。

- **类型**：组件输入为 **`PropertyDescription`**；在 `types.ts` 上按需补充可选 **`valueText`** 供设计时配置文本；自定义输出由面板将 `CustomOutputRow` 映射为 `PropertyDescription`（填 `key`/`name`/`valueText`）。

- **行为**：不改变 BPM 模型写入协议与后端 API；仅展示层抽象。


