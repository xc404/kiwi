## Context

`BpmEditor` 已通过 `ProcessDesignService.getProcessById` 加载 `bpmProcess`，并在保存、部署后更新同一 signal。后端 `BpmProcess` 继承 `BaseEntity`，包含 `name`、`updatedTime`（JSON 字段名以序列化配置为准，常见为 `updatedTime`）、`createdTime` 等；流程专有字段含 `version`、`deployedVersion`、`deployedAt`、`projectId` 等。页面模板当前在 `bpm-editor-canvas-content` 内为 `top-bar`（工具栏）+ 画布，适合在 **工具栏上方** 或 **顶栏左侧信息 + 右侧工具栏** 两种布局中选一种。

## Goals / Non-Goals

**Goals:**

- 顶部信息区一眼可读：名称、修改时间、部署时间（无部署时明确展示「未部署」或「—」）。
- 与 `bpmProcess` 同步：加载完成后展示；保存/部署成功后自动刷新展示（依赖现有 `bpmProcess.set`）。
- 响应式：窄屏时可换行或收缩次要字段，避免挤压画布。

**Non-Goals:**

- 不在此区域编辑流程名称（若需内联编辑，另开变更）。
- 不新增统计类接口或轮询服务端。

## Decisions

1. **布局**  
   在 `top-bar` 上方新增一行 `bpm-editor-process-meta`（或合并为一行 flex：左侧 meta、右侧保留 `bpm-toolbar`），与现有 `bpm-project-process` 工具条视觉语言对齐（浅底、细边框可选）。

2. **时间格式**  
   使用与项目一致的日期展示方式（如 `DatePipe` 或统一工具函数）；`deployedAt` 为空时表示未部署。

3. **字段映射**  
   在组件内用 `computed` 或模板中安全导航读取 `bpmProcess()`；若当前代码使用 `updatedAt` 而后端为 `updatedTime`，实现时统一为接口实际字段，避免一直为 `undefined`。

4. **加载态**  
   `bpmProcess()` 为 `null` 时显示加载中；关键字段缺失时显示「—」。

## Risks / Trade-offs

- 字段过多时顶栏变高 → 采用次要信息折叠或仅展示核心三项 + tooltip。
- 国际化：若项目后续要 i18n，文案需抽离；首版可用中文标签。

## Migration Plan

仅前端发布，无迁移。

## Open Questions

无。
