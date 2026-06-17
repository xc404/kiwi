## Why

`kiwi-admin/frontend` 中主壳层放在 `layout/default/` 下，且根路由通过 `path: 'default'` 懒加载，导致业务 URL 统一带 `/default` 前缀；目录命名与路由层级重复，和「只有一个主布局」的意图不一致，也增加了硬编码路径与后续维护成本。

## What Changes

- 将主布局从 `src/app/layout/default/` 迁出并扁平化（例如统一到 `src/app/layout/` 下单一壳组件及子组件目录），**移除** `layout/default` 这一层目录；仅保留一套主布局（原 Default 壳）。
- 调整 `app.routes.ts`：**去掉** `default` 这一层路由；根路径 `''` 在登录守卫通过后直接加载主壳及其子路由（或等价结构），业务路径从 `/default/...` 变为 `/...`（如 `/dashboard/...`）。
- 全量替换代码与模板中对 `/default`、`default/...` 的导航、链接、URL 判断（含 `tab.service`、`nav-bar` 的 refresh-empty 判断、`routerLink`、`navigate`/`navigateByUrl` 等）；登录成功后的默认跳转路径同步更新。
- **BREAKING**：对外可收藏 URL、文档、集成方若写死 `/default/...` 需改为新路径；若「系统管理-菜单」等后端/配置中存储的路由以 `/default` 为前缀，需在实施阶段一并迁移或兼容说明。

## Capabilities

### New Capabilities

- `admin-app-shell`: 定义 kiwi-admin 前端主壳（单布局）与顶层路由约定：认证后业务路由不再包含 `default` 段，刷新占位等特殊路径随新结构一致。

### Modified Capabilities

（无：现有 `openspec/specs/` 下无与本变更重叠的既有能力规格。）

## Impact

- **代码**：`kiwi-admin/frontend/src/app/app.routes.ts`、`layout/**`、各页面与 shared 组件中硬编码路径、可能存在的 `tsconfig` 路径别名引用。
- **配置/数据**：侧栏菜单、权限路由若存绝对路径，需核对是否含 `/default` 前缀并更新。
- **依赖**：无新增运行时依赖；为纯结构与路由重构。
