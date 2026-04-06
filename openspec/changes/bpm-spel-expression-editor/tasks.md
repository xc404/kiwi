## 1. 实现

- [x] 新增 `bpm-spel-variable-context.ts`：反向图、引用扫描、`buildSpelVariableSuggestions`。
- [x] 新增 Formly `SpelExpressionEditorType`（CodeMirror + 补全 + 工具栏）。
- [x] 注册 `spel-expression`；`PropertyGroup` 传入 `spelVariables`。
- [x] `bpm-editor.ts` 导出变量推导 API。
- [x] 依赖：`@codemirror/state`、`view`、`language`、`commands`、`autocomplete`、`lang-javascript`。

## 2. 可选后续

- [ ] 在具体组件（如需要 SpEL 的 HTTP/赋值扩展）上将 `htmlType` 设为 `spel-expression`。
- [ ] 若运行时统一 SpEL 求值，补充后端测试与文档。
