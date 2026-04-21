# BPM 设计器 AI 助手

## ADDED Requirements

### Requirement: 设计器内 AI 入口

系统 SHALL 在 BPM 流程设计器页面（`bpm-editor`）提供 AI 对话入口，登录用户可见；当 AI 功能未启用时 SHALL 不发起后端调用或 SHALL 展示明确不可用提示。

### Requirement: 工具栏等价操作

系统 SHALL 支持通过 AI 返回的结构化动作执行与现有 `BpmToolbar` 一致的受控操作，包括但不限于：撤销/重做、复制/粘贴、删除选中、缩放与适应画布、查找、保存、部署、启动流程、另存为组件、导出 BPMN/SVG（以产品确认的子集为准）。

### Requirement: BPMN 修改

系统 SHALL 支持用户通过自然语言意图，经后端解析后返回可应用的 BPMN XML 或等价增量指令；前端在应用前 SHALL 经过与后端一致的校验策略，失败时向用户展示可读错误且 SHALL NOT 静默损坏当前画布。

### Requirement: 组件智能追加

系统 SHALL 支持根据用户描述匹配组件库中的组件元数据，并返回结构化「追加组件」动作；前端 SHALL 使用与手动从组件面板拖拽/上下文菜单一致的建模路径将组件落入流程图。

### Requirement: 安全与权限

系统 SHALL 仅允许对已授权的流程定义上下文执行修改类动作；服务端 SHALL 校验请求中的流程标识与当前用户权限；BPMN 负载 SHALL 受大小与基本结构校验约束。
