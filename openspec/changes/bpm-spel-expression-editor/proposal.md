## Why

流程组件输入参数需要支持 **Spring SpEL** 表达式编辑；设计器侧仅有普通文本框，无法插入变量、缺少与流程图一致的变量提示，易与后端 `BpmComponentParameter` 及流程 IO 分析约定（`${varName}` 引用）脱节。

## What Changes

- 前端新增 **SpEL 表达式编辑器**（Formly 类型 `spel-expression`），基于 CodeMirror 6，支持：
  - 输入 `$` / `${` 时对**图中已引用变量**与**当前节点上游组件输出变量**进行自动完成；
  - 工具栏：插入变量（`${key}`）、插入常用 SpEL 片段。
- 在 `property-group` 中根据当前 BPMN 元素与 `ComponentService` 推导变量列表并传入编辑器。
- `bpm-editor.ts` 重新导出 `buildSpelVariableSuggestions`，便于同模块内复用。
- 组件元数据：`BpmComponentParameter.htmlType = "spel-expression"` 时属性面板使用该编辑器（与现有 `assignments-editor` 等并列）。

## Capabilities

### New Capabilities

- `bpm-spel-expression-editor`：SpEL 编辑、变量补全与插入能力。

### Modified Capabilities

- （无对后端契约的破坏性变更；可选在后续变更中为具体组件参数标注 `htmlType`。）

## Impact

- **前端**：新增依赖 `@codemirror/*`；`kiwi-admin/frontend` 属性面板与 Formly 注册。
- **后端**：无必选改动；运行时若解析 SpEL，由现有或后续组件实现。
