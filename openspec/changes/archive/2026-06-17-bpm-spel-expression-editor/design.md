## Context

- 后端 `BpmProcessIoAnalysisService` 使用正则 `\$\{([a-zA-Z0-9_]+)\}` 从 `camunda:inputParameter` 文本中提取变量引用；上游输出按 `sequenceFlow` 反向可达的前驱组件 `outputParameters.key` 合并。
- 前端属性面板通过 `ComponentPropertyProvider` + `PropertyNamespace.inputParameter`（及 CallActivity 的 `In`）读写 Camunda 扩展元素。

## 设计

### 变量推导（与后端语义对齐）

- **referenced**：遍历图中所有 `bpmn:ServiceTask` / `bpmn:CallActivity`，对每个组件输入参数取值字符串，匹配 `${var}`，去重排序。
- **upstream**：对当前选中元素 id，在 `sequenceFlow` 反向图上 BFS 前驱；对前驱中的组件任务，读取其 `BpmComponent` 元数据 `outputParameters` 的可见 `key`。
- 合并顺序：先上游输出，再图中引用（去重时保留首次出现顺序）。

### 编辑器

- Formly 类型名：`spel-expression`，注册于 `shared/formly/public_api.ts`。
- CodeMirror：`@codemirror/lang-javascript` 作近似高亮；自动完成在每次触发时通过闭包读取最新 `spelVariables`（避免图变更后需重挂载编辑器）。
- 补全触发：`$` 仅、`$ident`、`\$\{` 内部分标识符；插入 `${key}` 与后端分析一致。

### 使用方式

在组件定义（Java `@ComponentParameter` 或 OpenAPI 生成）中为参数设置 `htmlType = "spel-expression"`。
