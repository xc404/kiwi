## Context

当前 Angular 应用在 `app.routes.ts` 中用 `path: 'default'` 懒加载 `./layout/default/default-routing`，壳组件为 `DefaultComponent`，子路由（dashboard、system、bpm 等）挂在 `default-routing` 的 `children` 下，因此浏览器路径为 `/default/<segment>/...`。布局相关组件全部位于 `src/app/layout/default/` 子目录中。仓库内已有多处硬编码 `/default/...` 或 `default/dashboard/...`。

## Goals / Non-Goals

**Goals:**

- 物理上删除 `layout/default/` 目录层级：布局壳及其子组件（side-nav、nav-bar、tab、tool-bar、nav-drawer、setting-drawer、refresh-empty 等）迁移到单一、清晰的 `layout/` 树下（例如直接位于 `src/app/layout/<feature>/` 或与壳同级的子文件夹），仍只保留**一套**主布局行为。
- 顶层路由去掉 `default` 段：根路径重定向到业务首页（如 `dashboard`）或空路径下由壳组件承载子路由，使对外 URL 为 `/dashboard/...`、`/system/...` 等形式。
- 与 tab 刷新、`refresh-empty`、登录后跳转、菜单导航相关的逻辑全部与新路径一致。
- 识别并记录需同步的配置数据（如菜单表中的 path）。

**Non-Goals:**

- 不引入第二套布局或多主题壳拆分。
- 不改变 BPM 设计器/查看器已独立在 `app.routes` 顶层的懒加载组件路由（除非路径拼接与本次冲突）。
- 不做大范围 unrelated 的 frontend 目录重组（仅围绕 layout 与 `/default` 路由移除）。

## Decisions

1. **目录落位**  
   - **选择**：将原 `layout/default/` 下内容整体上移到 `src/app/layout/`，子模块保持相对子目录名（如 `side-nav/`、`setting-drawer/`），壳组件文件可重命名为 `app-shell.component.*`（或暂保留 `default.component.*` 但路径不再含 `default/` 文件夹——以实施时最小破坏为准）。  
   - **理由**：满足「移除 layout default 目录」；子组件文件夹名保持语义，减少无意义重命名。  
   - **备选**：新建 `layout/shell/` 再嵌套一层——增加深度，非首选。

2. **路由拼装方式**  
   - **选择**：在 `app.routes.ts` 中定义 `path: ''`（或单一懒加载模块）`canActivate: [JudgeLoginGuard]`，`loadChildren` 指向新的 `layout-routing.ts`（由 `default-routing` 重命名/合并），其根 `path: ''` 挂壳组件，`children` 保持现有各 `pages/*` 的 `loadChildren` 不变。根 `''` 的 `redirectTo` 改为 `dashboard`（或当前等价默认页）。  
   - **理由**：去掉 URL 中的 `default` 段，同时保留懒加载与现有子路由模块结构。  
   - **备选**：把所有子路由内联进 `app.routes.ts`——文件过大，维护差。

3. **路径与别名**  
   - **选择**：所有 `@app/layout/default/...` 的 TS 导入改为 `@app/layout/...`（或具体新文件名）；`router.navigate`、`routerLink`、`navigateByUrl`、字符串比较中的 `/default` 前缀一律更新。  
   - **理由**：与物理路径一致，避免残留旧前缀。

4. **菜单/后端配置**  
   - **选择**：在 tasks 中增加「全库/DB 检索 `/default` 菜单 path」与迁移步骤；若数据库存旧 path，提供 SQL 或管理端批量替换说明。  
   - **理由**：仅改前端路由会导致侧栏链接 404。

## Risks / Trade-offs

- **[Risk] 已分享书签与外部文档使用旧 URL** → 可在网关或 Angular `UrlMatcher` 做短期重定向（非必须，可在 Open Questions 中决定是否做）；至少要在变更说明中标注 **BREAKING**。  
- **[Risk] 遗漏某处硬编码 `/default`** → 实施时用全局搜索 `/default/`、`'/default'`、`"default/`（排除 `default_face.png` 等资源名）并补充 e2e/手工回归清单。  
- **[Trade-off] 组件/选择器仍名 default** → 可后续单独重构为 shell，本次以目录与路由为主。

## Migration Plan

1. 合并路由与移动文件后本地全量 `ng build` / 测试。  
2. 更新环境相关文档中的示例 URL。  
3. 若有菜单数据：在测试/生产按清单执行 path 更新；必要时配置 HTTP 301（基础设施层）。  
4. 回滚：恢复 `app.routes` 与 `layout/default` 目录及旧 path（git revert）。

## Open Questions

- 默认落地页是否仍为 `dashboard/analysis`（与当前登录跳转一致），仅去掉前缀。  
- 是否在首版加入从 `/default/*` 到 `/*` 的临时重定向以平滑迁移（需产品/运维确认）。
