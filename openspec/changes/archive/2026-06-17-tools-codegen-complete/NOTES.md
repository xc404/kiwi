# 归档说明（由 Cursor plan 迁入）

**日期：** 2026-06-17

本 change 源自 `.cursor/plans/代码生成工具完成_0eff4d51.plan.md`；**已实现**后归档，plan 文件已删除。

## 实现路径（以代码为准）

- `GenService.renderFiles` / `previewCode` / `buildZip`；`GeneratedFile` record
- `CodeGenEntityCtl`：`GET /{id}/preview`、`GET /{id}/download`（ZIP）
- Velocity 模板：Mongo / MyBatis-Plus / Angular / `integration/` 菜单与路由片段
- 前端：`codegen-preview-modal`、导入向导修复、ZIP 下载
- 已删除重复 `CodeGenConfig`（`code.gen`）；统一 `GenConfig`（`app.gen`）

## Main spec

无 delta spec；未同步至 `openspec/specs/`。
