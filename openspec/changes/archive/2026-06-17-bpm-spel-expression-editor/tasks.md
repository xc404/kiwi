## 1. 实现

- [x] 变量上下文：`expression-variable-context.ts` + `BpmExpressionVariableService`（初稿 `bpm-spel-variable-context.ts` / `buildSpelVariableSuggestions` 已演进）
- [x] 新增 Formly `SpelExpressionEditorType`（CodeMirror + 补全 + 工具栏）
- [x] 注册 `spel-expression`；`PropertyGroup` 传入 `spelVariables`
- [x] `bpm-editor.ts` 导出 `SpelVariableSuggestion` 等类型供复用
- [x] 依赖：`@codemirror/state`、`view`、`language`、`commands`、`autocomplete`、`lang-javascript`

## 2. 可选后续

- [x] 2.1 （延后）在具体组件参数上标注 `htmlType = spel-expression` — 编辑器已就绪，按组件需求单独变更
- [x] 2.2 （延后）运行时统一 SpEL 求值与后端测试 — 非本 change 范围
