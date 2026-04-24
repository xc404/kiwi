# `src/app` 目录约定（kiwi-admin 前端）

本文约定 **顶层目录职责**、**路径别名** 与 **新增代码放置规则**，便于一致地归类与检索。

## 顶层一览

| 目录 | 职责 |
|------|------|
| `core/` | 与具体页面无关的**单例能力**：HTTP、拦截器、全局 store、通用懒加载等。不放可复用 UI 组件。 |
| `shared/` | **跨业务复用**的 UI 与行为：`components/`、`modal/`、`biz-components/`、指令、管道、formly、全局模板、抽屉封装等。 |
| `layout/` | 应用壳：侧栏、顶栏、页签、主题设置等路由外壳布局。 |
| `pages/` | **按路由域**组织的页面（如 `bpm/`、`system/`）。域内再按**功能子域**分子目录（见下文 BPM 示例）。 |
| `utils/` | 纯函数、校验器等**无 Angular 注入依赖**的工具（若强依赖 `inject()`，优先放 `core/services`）。 |
| `config/` | 应用级静态配置（常量、环境相关映射等）。 |

## 路径别名（`tsconfig.json`）

| 别名 | 物理路径 |
|------|----------|
| `@shared/*` | `src/app/shared/*` |

与模态、通用组件相关的 import 请使用 **`@shared/modal/...`**、**`@shared/components/...`**（不再使用已移除的 `@widget/*`）。

## `shared/modal` 与 `shared/biz-components` 怎么选

| 放 `shared/modal/` | 放 `shared/biz-components/` |
|--------------------|------------------------------|
| **模态基类**（`base-modal.ts`）、`ModalWrapService`、`ModalBtnStatus` 等与 ng-zorro `NzModal` 强绑定的基础设施 | 布局壳上的 **小块 UI**（头部菜单旁入口、首页通知条等），不封装整套弹窗生命周期 |
| **可拖拽封装**：`nz-modal-wrap.service`、`modal-drag.directive`、`modal-drag.service` | 仅 **展示 + 少量 @Input**，调用方自己决定何时渲染 |
| **业务弹窗子目录**（如 `login/` 登录过期、`change-password/` 改密）——与 `ModalWrapService` 成套使用 | 与模态生命周期无关的纯业务片段 |

## `shared/modal` 目录（当前）

| 文件 / 子目录 | 用途 |
|---------------|------|
| `base-modal.ts` | 模态封装基类、`ModalBtnStatus`、`ModalWrapService` 等 |
| `nz-modal-wrap.service.ts` | 创建可拖拽对话框 |
| `modal-drag.service.ts`、`modal-drag.directive.ts` | 拖拽实现 |
| `login/` | 登录过期弹窗（service + component） |
| `change-password/` | 修改密码弹窗（service + component） |

## `shared/components` 补充（由原 widgets 迁入）

在通用 `components/` 下增加与业务弱耦合、可复用的块（与 `crud/` 等并列）：

| 子目录 | 用途 |
|--------|------|
| `dict-selector/` | 字典下拉选择（如代码生成向导） |
| `lock-widget/` | 锁屏 |
| `search-route/` | 路由搜索弹层 |
| `common-tree-selector/` | Formly 等使用的树形选择（`biz-tree-seletor.ts`） |

## `shared` 其他子目录（当前）

| 子目录 | 用途 |
|--------|------|
| `components/` | 通用展示与交互组件（含上表子目录及 `crud/` 等）。 |
| `directives/`、`pipes/` | 指令与管道。 |
| `formly/` | Formly 字段与面板扩展。 |
| `biz-components/` | 见上表，与 `modal` 区分。 |
| `dict/` | 字典相关共享能力。 |
| `global-templates/` | `createComponent` / `InjectionToken` 挂载的全局模板片段。 |
| `drawers/` | 抽屉封装与示例内容。 |

## `pages/bpm` 功能子域（当前）

| 子目录 | 含义 | 路由提示 |
|--------|------|----------|
| `flow-elements/` | 流程扩展元素 / **组件库**（对接 REST `/bpm/component/*`），原 `component/` | `com` |
| `design/` | 流程设计器（BPMN 编辑、属性面板、调色板等） | 由上层路由懒加载 |
| `project/` | BPM 项目与项目内流程列表 | `project`、`process` |
| `runtime/` | 运行态（流程实例列表等），原 `process-instances/` | `processinstances` |

**说明**：子目录名与 **URL path** 不必一致；路由在 `bpm-routing.ts` 中显式配置。后端 API 路径不因前端文件夹改名而改变。

## `core/services` 子目录说明（当前）

- `common/`：通用运行时服务（懒加载、页签等）。
- `http/`：按后端域拆分的 API 服务。
- `interceptors/`：HTTP 拦截器。
- `store/`：全局状态 store。
- `validators/`：可复用校验逻辑。

## 新增文件自检清单

1. 是否只属于某一业务路由？是 → `pages/<域>/`，必要时再分子域目录。
2. 是否无 UI、全局单例？是 → `core/` 或 `utils/`。
3. 是否与 **NzModal / ModalWrapService** 成套？是 → `shared/modal/`。
4. 是否多页复用的通用 UI 块（非模态基座）？是 → `shared/components/` 合适子目录或 `shared/biz-components/`。
5. 是否与壳布局强绑定？是 → `layout/`。

## 历史调整说明

- 原 `app/tpl/` → `app/shared/global-templates/`。
- 原 `app/drawer/` → `app/shared/drawers/`。
- 原 `app/widget/` 曾迁至 `shared/widgets/`；现已 **拆分**：模态相关 → **`shared/modal/`**，其余组件 → **`shared/components/`** 下对应子目录；**`@widget/*` 别名已移除**。
- 原 `pages/bpm/component/` → `pages/bpm/flow-elements/`；`pages/bpm/process-instances/` → `pages/bpm/runtime/`。
- 已删除历史上无引用的旧弹窗实现（见 Git 历史）。

迁移 import：`@widget/...` → `@shared/modal/...` 或 `@shared/components/...`。
