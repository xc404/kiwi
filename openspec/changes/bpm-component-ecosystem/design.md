## Context

- Kiwi 组件契约：`@ComponentDescription` + `JavaDelegate`，自动注册经 `ClasspathBpmComponentProvider`。
- 流程模型：[`BpmProcess.projectId`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/model/BpmProcess.java) 关联 [`BpmProject`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/model/BpmProject.java)。
- 启动入口：[`BpmProcessStartService.start`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/service/BpmProcessStartService.java) 当前仅合并用户 `variables`。
- 加密先例：[`ConnectionService`](kiwi-admin/backend/src/main/java/com/kiwi/project/tools/jdbc/connection/service/ConnectionService.java) 使用 `AesUtil` + `PasswordService.getPasswordSecret()`。

## Goals / Non-Goals

**Goals:**

- 单表 `bpmProjectEnvVar`，`projectId` 为外键，`(projectId, key)` 唯一。
- 项目设置界面 CRUD；加密项不回显明文。
- 启动流程时注入项目 env；组件 BPMN 可用 `${KEY}` 引用。
- 示例模块 `kiwi-bpmn-component-example` 含 `DemoGreetingActivity`。

**Non-Goals:**

- 多 profile（dev/staging/prod）子环境；Vault 外部集成；插件 JAR 加载；内置组件库扩充。

## Decisions

1. **独立集合** `bpmProjectEnvVar`，不嵌入 `BpmProject` 文档——便于分页、唯一索引与权限校验。
2. **加密标记** `encrypted: boolean`：`true` 时 value AES 存储，API 列表/详情不返回 value；PUT 时空 value 表示不修改。
3. **启动注入**：`BpmProcessStartService` 读取 `bpmProcess.projectId` → `BpmProjectEnvService.loadDecrypted(projectId)` → 与用户 variables 合并（**用户优先**）。
4. **变量类型**：`encrypted=false` → `setVariables`；`encrypted=true` → `setTransientVariables`（Operaton 瞬态，不进历史）。若 API 不可用则全部 `setVariables` 并文档标注风险（实施时以引擎实测为准）。
5. **示例模块**：`kiwi-bpmn-component-example` 作为 backend 可选依赖编入 classpath；`@Component("demoGreeting")`，group「示例」。
6. **权限**：环境变量 API 经 `BpmOwnershipAccessService.assertOwnsProject`；权限码 `bpm:project:env:*`（或复用项目 update 权限）。

## Risks / Trade-offs

- **[Risk] 瞬态变量在子流程/异步边界不可见** → 文档说明加密 env 仅当前执行作用域；复杂场景用非加密 + 服务端解析。
- **[Risk] 非加密 env 进入历史变量** → 仅 URL 等非敏感项标 `encrypted=false`。
- **[Risk] projectId 为空的老流程** → 跳过 env 加载，行为与现网一致。

## Migration Plan

- 无破坏性变更；新集合自动创建；示例模块默认编入 backend dev 依赖或 profile 可选。

## Open Questions

- 是否在启动弹窗展示「将注入的环境变量 key 列表」（首期不做，仅文档说明）。
