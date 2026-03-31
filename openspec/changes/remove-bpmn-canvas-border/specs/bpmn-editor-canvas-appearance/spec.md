## ADDED Requirements

### Requirement: Editor canvas has no diagram-js container border

BPM 设计器页面中，由 bpmn-js diagram-js 注入的画布根容器（挂载于编辑器画布区域） SHALL 不显示可见的容器边框或轮廓线，该效果 SHALL 通过样式覆盖默认 `diagram-js` 外观实现，且 SHALL NOT 影响 BPMN 图元（连线、形状、泳道等）的绘制描边。

#### Scenario: Default chrome border is not visible

- **WHEN** 用户打开 BPM 流程设计器并加载或新建流程图
- **THEN** 画布区域不出现由 diagram-js 默认样式产生的容器外框边框（用户可感知为围绕可编辑区域的整圈线框）

#### Scenario: Modeling behavior unchanged

- **WHEN** 用户在画布上选择、拖拽、缩放或编辑元素
- **THEN** 交互行为与变更前一致，仅去除容器级边框相关视觉
