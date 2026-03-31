## ADDED Requirements

### Requirement: Properties Panel Supports Long Content Scrolling
BPM 设计器在右侧属性面板内容超出可视高度时，系统 MUST 提供可用的纵向滚动能力，并保证用户可以访问所有属性项与操作控件。

#### Scenario: Long properties list remains accessible
- **WHEN** 用户选中一个包含大量属性字段的 BPM 元素，且属性总高度超过视口高度
- **THEN** 右侧属性面板区域出现纵向滚动并可滚动到底部访问全部字段

#### Scenario: Scrolling is isolated to properties panel
- **WHEN** 用户在右侧属性面板区域滚动鼠标滚轮或触控板
- **THEN** 仅属性面板区域滚动，画布主区域不发生联动滚动或错位

### Requirement: Editor Layout Remains Stable Under Overflow
BPM 设计器在属性面板溢出场景下，系统 SHALL 维持画布区与属性面板区的分栏布局稳定，不得发生裁切、重叠或高度塌陷。

#### Scenario: Overflow does not break split layout
- **WHEN** 属性面板内容从少量变为大量并触发滚动
- **THEN** 画布区域与右侧属性面板宽度分配保持稳定，顶部工具栏与画布可见区域不被异常挤压
