## Why

Kiwi 已有 `@ComponentDescription` + `JavaDelegate` 组件体系，但第三方开发缺少可复制的示例模块；敏感配置（API Key、业务 URL）也缺少按 **BPM 项目** 隔离、在界面配置并在 **启动流程时注入** 的机制（类 Vault 项目 env）。需要补齐「示例脚手架」与「项目环境变量」两块基础能力，支撑后续组件生态扩充。

## What Changes

- 新增 Maven 模块 `kiwi-bpmn-component-example`，内含 `DemoGreetingActivity` 演示组件（`@ComponentDescription` + `JavaDelegate`）及 README。
- 新增 Mongo 实体 `BpmProjectEnvVar`（`projectId` 外键，key/value，`encrypted` 标记），CRUD API 与前端管理界面（项目下「环境变量」Tab）。
- 调整 `BpmProcessStartService`：启动流程时加载所属 `projectId` 的环境变量，与用户传入 variables 合并后注入引擎（加密项使用瞬态变量，非加密项为普通变量；同名 key 用户 variables 优先）。
- 文档：在 `docs/bpm-component.zh-CN.md` 补充第三方组件开发与项目环境变量说明。

## Capabilities

### New Capabilities

- `bpm-project-env-var`：BPM 项目级环境变量（表结构、加密存储、管理 API/UI、启动注入）。
- `bpm-component-example`：第三方组件开发示例模块（DemoGreeting + 接入说明）。

### Modified Capabilities

- （无；`openspec/specs/` 下暂无存量规格需 delta。）

## Impact

- **后端**：`BpmProjectEnvVar` 模型/Dao/Service/Ctl；`BpmProcessStartService`；可选 backend 依赖 `kiwi-bpmn-component-example`。
- **前端**：项目详情页环境变量 CRUD；`bpm-routing` 或项目子路由。
- **模块**：`kiwi-bpmn/kiwi-bpmn-component-example`；根 `pom.xml` / `kiwi-bpmn/pom.xml` 注册模块。
- **数据**：新 Mongo 集合 `bpmProjectEnvVar`，`(projectId, key)` 唯一索引。
- **安全**：加密项 API 不回显明文；加密值使用 `AesUtil` + `app.password.secret`。
