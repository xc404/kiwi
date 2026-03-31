## Why

共享 `crud-page` 组件承载列表、搜索与弹窗编辑，长期迭代中遗留拼写错误（`colunms`）、`PageConfig` 类型书写不统一，降低可读性并与子组件 `CrudEditForm` 的 `columns` 命名不一致。在不大改行为的前提下做一次集中清理，便于后续维护。

## What Changes

- 将 `PageConfig.editModal.colunms` 更正为 `columns`，与 `CrudEditForm` 及常见语义一致。**BREAKING**：若外部曾传入 `colunms`（本仓库内未发现），需改为 `columns`。
- 统一 `PageConfig` 接口成员分隔符与类型定义风格（分号、可选属性一致性）。
- 精简 `CrudPage` 中可合并的重复逻辑（如弹窗打开时的 record 默认值），不改变对外 API 行为。

## Capabilities

### New Capabilities

- `crud-page`：定义共享 CRUD 页配置与编辑弹窗布局选项的规范性要求（命名与类型一致性）。

### Modified Capabilities

- （无全局 `openspec/specs` 基线；本次为新增能力规格。）

## Impact

- **代码**：`kiwi-admin/frontend` 下 `shared/components/crud/components/crud-page.ts`、`crud-page.html`；若有测试或 Story 引用旧字段则一并更新（当前检索无 `colunms` 外部引用）。
- **行为**：运行时行为保持不变；仅配置字段名从错误拼写改为正确拼写。
