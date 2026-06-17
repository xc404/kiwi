## Why

`/tools/codegen` 菜单与 CRUD 骨架已有，但生成主链路未完成：预览写死磁盘路径、模板不全、无 ZIP 下载与预览 UI，导入向导存在 bug。

## What Changes

- 重构 `GenService`：内存渲染 `renderFiles`，预览与 ZIP 共用
- 按 `daoTpl`（MongoDB / MyBatis-Plus）+ `webTpl`（Angular）生成后端、前端与 `integration/` 片段
- `GET /{id}/preview`、`GET /{id}/download`；权限 `gen:entity:generate`
- 前端预览 Modal（文件树 + 代码）与下载按钮；修复导入向导与字段模态

## Capabilities

### New Capabilities

- （无 main spec。）

### Modified Capabilities

- （无。）

## Impact

- `com.kiwi.project.tools.codegen.*`
- `kiwi-admin/frontend/src/app/pages/tools/codegen/**`
- `resources/vm/**`

## 非目标

- 自动改写 `layout-routing.ts` 或 `R__SysMenu.json`（ZIP 提供可合并片段）
- Vue 旧模板纳入生成清单
