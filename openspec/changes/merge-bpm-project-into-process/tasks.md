## 1. 菜单与路由

- [x] 1.1 `R__SysMenu.json`：下线 `bpm_process_definition`（删除或 `visible: false`），保留 `bpm_project` → `/bpm/project`
- [x] 1.2 `bpm-routing.ts`：`project` 加载流程列表页；`process-definition` redirect 到 `project`（保留 `projectId`）；默认仍跳 `project`
- [x] 1.3 模板市场 / 远程市场安装完成后 `navigate(['/bpm/project'], { queryParams: { projectId } })`

## 2. 项目管理页

- [x] 2.1 在流程列表页补齐新建项目、重命名、环境变量、空状态「新建项目」
- [x] 2.2 删除纯项目列表页 `bpm-project.ts`；行操作「流程管理」改为「设计」；隐藏项目 ID 列
- [x] 2.3 当前项目被删后清工作区记忆并切到另一项目或空状态

## 3. 删除项目

- [x] 3.1 `BpmProjectCtl`：有流程则拒绝删除；无流程则级联删除 `BpmProjectEnvVar`

## 4. 文档

- [x] 4.1 `docs/bpm-component.zh-CN.md` 路径改为 工作流 → 项目管理
