## Context

工作流菜单同时挂「项目管理」`/bpm/project`（`BpmProject` CRUD 列表）和「流程管理」`/bpm/process-definition`（`BpmProjectProcess`：选项目 + 该项目下流程）。项目是流程、环境变量、模板包的容器（`BpmProcess.projectId`、`BpmProjectEnvVar`），不能扁平化。删除项目目前只删文档，会留下悬挂流程与 env。

## Goals / Non-Goals

**Goals:**

- 侧栏唯一设计入口为「项目管理」`/bpm/project`，该页同时承担项目切换/生命周期与流程列表。
- 旧路径 `/bpm/process-definition` redirect 到 `/bpm/project` 并保留 `projectId`。
- 记住上次 `projectId`，进入项目管理时自动选中。
- 删除非空项目失败；空项目删除时级联清 env。

**Non-Goals:**

- 不删除 `BpmProject` / `projectId`，不把环境变量或模板改挂到单条流程。
- 不用顶级路径 `/bpm-project`。
- 不在后端持久化「用户默认项目」。

## Decisions

1. **路由挂载**：`project` 加载现 `BpmProjectProcess`；删除纯列表组件 `bpm-project.ts`。`process-definition` 用 Angular `redirectTo: 'project'`。Query `projectId` 靠浏览器在 redirect 时保留（同级 redirect + 现有 `queryParamsHandling`）；若框架丢 query，则用带 `redirectTo` 的自定义 guard 或 `queryParams` 合并导航。优先标准 redirect，验证时确认 `projectId` 仍在。
2. **菜单**：`R__SysMenu.json` 去掉或 `visible: false` `bpm_process_definition`；保留 `bpm_project`。Repeatable 迁移下次启动覆盖菜单。
3. **删除语义**（`BpmProjectCtl.delete`）：按 `projectId` 计流程数，`>0` 则 409/400 并提示；否则删 env 再删项目。不级联删流程，避免误伤。
4. **文案**：用户侧仍称「项目」；流程行操作「流程管理」改为「设计」（打开 `/bpm/design/:id`）。
5. **工作区记忆**：沿用 `kiwi.bpm.lastWorkspaceProjectId`。无 query 时读记忆，再退回项目列表第一项；无项目时空状态「新建项目」。当前项目被删后清记忆并切到另一项目。

## Risks / Trade-offs

- **[Risk] redirect 丢掉 `projectId`** → 验收时用带 query 的旧 URL 测；必要时改为显式 `navigate`。
- **[Risk] 菜单 repeatable 未跑** → 旧「流程管理」仍可见；依赖现有 Mongo 启动迁移。
- **[Risk] 删除拒绝被当成 bug** → 错误信息写明须先删或迁走流程。

## Migration Plan

- 无数据迁移。发版后旧书签 `/bpm/process-definition?projectId=` 应落到项目管理页。
- 回滚：恢复菜单可见性与 `bpm-routing` 两条独立路由。

## Open Questions

- （无。入口名称与 `/bpm/project` 已确认。）
