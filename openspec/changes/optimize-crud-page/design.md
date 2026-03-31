## Context

`CrudPage` 使用 Angular 19+ signals、`input()`、`computed()` 组合 `CrudDataSource` 与表格/树表/行内编辑。`PageConfig` 在单文件内定义，供多个业务页传入。`CrudEditForm` 已使用 `columns` 表示表单列数。

## Goals / Non-Goals

**Goals:**

- 配置项命名与子组件、英语拼写一致（`columns`）。
- `PageConfig` 类型声明清晰、风格统一，便于 IDE 提示与 Code Review。
- 不改变 CRUD HTTP、权限、表格分页与弹窗保存流程的语义。

**Non-Goals:**

- 重命名 `crud-datastrore.ts` 文件名或重构 `CrudDataSource` 类结构。
- 将 `computed` 中的 `new CrudDataSource` 改为单例缓存（需更多验证，避免单独变更引入回归）。

## Decisions

1. **字段重命名**：采用 `columns` 替代 `colunms`，在 `editModalOptions` 默认值与模板绑定中同步替换；不保留别名属性，避免双维护。
2. **PageConfig 格式**：接口内统一使用分号结束成员；修正 `initializeData` 等处逗号误用为分号（TypeScript 接口中逗号合法，但项目内以分号为主时统一为分号）。
3. **`_popupEditModal`**：`record = record || { ...this.defaultEditRecord } || {}` 中第二个 `|| {}` 在第一个分支已覆盖空值时冗余；简化为 `record ?? { ...this.defaultEditRecord }` 或等价清晰写法，避免 `||` 链误读。

## Risks / Trade-offs

- **[Risk]** 外部 fork 或私有分支若使用 `colunms` 会编译失败或运行期未生效 → **Mitigation**：在 proposal 标 **BREAKING**；本仓库 grep 确认无引用。

## Migration Plan

- 全局搜索 `colunms`，替换为 `columns`。
- 前端 `ng build` 或现有 CI 任务验证通过即可发布。

## Open Questions

- 无。
