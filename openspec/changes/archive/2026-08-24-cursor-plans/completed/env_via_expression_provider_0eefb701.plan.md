---
name: Env via Expression Provider
overview: 删除 fieldHint 实现，改为 BpmProjectEnvVariableProvider 贡献项目环境变量到表达式补全链。
todos:
  - id: extend-expression-model
    content: expression-variable / context 增加 projectEnv
    status: completed
  - id: designer-context
    content: BpmDesignerContextService + bpm-editor 设置 projectId
    status: completed
  - id: env-variable-provider
    content: BpmProjectEnvVariableProvider + multi token 注册
    status: completed
  - id: wire-suggestions
    content: property-group-edit / custom-output-row 统一走 BpmExpressionVariableService
    status: completed
  - id: remove-fieldhint
    content: 删除 fieldHint、hint 组件、projectEnvCatalog 透传
    status: completed
  - id: tests-docs
    content: Provider 单测 + 路线图更新
    status: completed
isProject: false
---

# 用 ExpressionVariableProvider 重构项目环境变量提示 — 已完成

实现见 `BpmProjectEnvVariableProvider`、`expression-variable-provider.registry.ts`、`BpmDesignerContextService`。
