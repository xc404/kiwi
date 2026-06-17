## Context

cryoEMS Movie 处理流水线已经迁移到 Kiwi-admin Camunda 编排（参见已归档变更 `cyroems-kiwi-workflow-integration` / `cryoems-bpm-movie-javadelegate-migration`）。当前 cryo-web 在 `MovieKiwiWorkflowService` 解析待启动的 BpmProcess 时只看两层：`Task.movieProcessDefinitionId`（已存在但 `@Hidden`、前端不暴露） → `app.kiwi.workflow.movie-process-definition-id`（全局回退）。实际部署里只能依赖全局回退，导致同一台 cryo-web 不能为不同 Task 选用不同流程，也无法准备即将到来的 mdoc（tomo 元数据）流水线接入。

涉及的三个仓库：
- `kiwi`（kiwi-admin，BPM 编排平台 + 前端）：拥有 `BpmProcess` 数据模型与 `GET /bpm/process` 列表 API。
- `cryo-em-server-backend-main-2 / cryo-web-server`：Spring Boot，作为 Kiwi 客户端启动 Movie 流程，定义 `Task` 模型与 `/api/task` REST。
- `cryo-em-server-frontend`：Next.js 15 App Router，创建任务页 `src/app/create/page.tsx` 通过 `GlobalSettings` 渲染所有任务级设置。

主要约束：
- Kiwi-admin 已有的 `KiwiClient`（PAT → Sa-Token）必须继续作为 cryo-web → Kiwi 的唯一受信通道，前端**不直连** Kiwi。
- 现有 `app.kiwi.workflow.movie-process-definition-id` 作为迁移期回退**保留**，本次改动不破坏其语义。
- Mongo 文档级演进：`Task.mdocProcessDefinitionId` 与 `BpmProcess.entry` 都是 Mongo 文档新增字段，老文档读出为 `null/false`，无需迁移脚本。
- 用户角色：普通用户即可创建任务并选择流程；不引入新角色或新权限位。

## Goals / Non-Goals

**Goals:**
- 在创建任务时让普通用户**显式选择**要使用的 Movie 流程；当数据集为 `is_tomo=true` 时**额外**选择 Mdoc 流程。
- 让 cryo-web 后端通过受控代理对外提供"按 type 列出可用入口流程"的能力，不暴露 Kiwi 内部 projectId 与子流程。
- 在 Kiwi-admin 端把"是否为入口流程"建模为流程自身的一个布尔属性，通过 BPM 设计器流程属性面板维护。
- 保持向后兼容：未配置 `process-types`、未升级前端、未升级 Kiwi 时，旧的 movie 全局回退继续工作。

**Non-Goals:**
- 不在编辑任务页（`src/app/edit`）允许更改已选流程；编辑模式仅展示且禁用。
- 不引入流程版本选择/锁定；当 Kiwi 重新部署一个入口流程产生新版本时，已写入 `Task.movieProcessDefinitionId` 的 BpmProcess id 不变，下一次 `startProcess` 仍用最新已部署版本（沿用 Kiwi 现有语义）。
- 不引入 ET / Export 等其他 type 的流程字段；本次只做 `movie` 与 `mdoc`。
- 不实现"流程市场 / 描述 / 截图"等富 UI；只做最小可用 Select。
- 不为 mdoc 流程实现完整的运行调度链路；本变更只到位"选择流程 + 字段保存 + Service 解析"，mdoc 实际触发由后续变更接管（保留可注入的 `MdocKiwiWorkflowService` 占位即可）。

## Decisions

### Decision 1: cryo-web 代理 vs 前端直连 Kiwi-admin

**Choice:** cryo-web 提供 `GET /api/bpm/processes?type={movie|mdoc}` 代理端点，前端只与 cryo-web 对话。

**Rationale:**
- 前端没有 Kiwi 的 Sa-Token，引入双套登录态成本高、安全面变大。
- cryo-web 已有 `KiwiClient` 封装 PAT→Sa-Token 兑换；复用零成本。
- 代理端点能在 cryo-web 侧做白名单（type→projectId）与简化 DTO（剥离 `bpmnXml`、`createdBy` 等无关字段），减少传输与暴露面。

**Alternatives considered:**
- 前端直接调 Kiwi `/bpm/process`：被否，跨域 + 凭据散落 + 暴露内部 projectId。
- 拷贝流程清单到 Mongo 缓存：被否，引入数据一致性问题，本次清单较短，直接转发即可。

### Decision 2: Kiwi 端用 `BpmProcess.entry` 字段标识入口

**Choice:** 给 `BpmProcess` 加 `entry: boolean`（默认 `false`），并新增 `GET /bpm/process/entries?projectId=...&deployed=true`，仅返回 `entry=true ∧ deployedVersion>0 ∧ deployedAt!=null` 的流程。

**Rationale:**
- 显式、用户可控（在 BPM 设计器流程属性面板勾选）、不歧义。
- 与现有 `BpmProcess` 的其他属性（`maxProcessInstances` 等）层级一致，无需新表/新模型。
- 子流程（被 callActivity 引用、用于复用的子片段）默认 `entry=false`，自然从下拉中过滤掉。

**Alternatives considered:**
- 在 `BpmProject` 上加 `exposed`：粒度过粗，同一项目可能既有入口流程又有共享子流程。
- 通过 BPMN XML 静态分析自动识别"未被 callActivity 引用"：跨项目分析成本高，可能误伤"独立可启动的子流程"。
- 引入 `category` 枚举：本次场景过度设计；type→projectId 映射已经做到分类。

### Decision 3: 默认值的三段回退顺序

**Choice:** 解析 `resolveBpmProcessId(task, type)` 按以下优先级：

```
① task.get<Type>ProcessDefinitionId()                ← 用户在创建页选的
② properties.processTypes[type]                     ← type-based 默认（按 is_tomo 选项 a/b）
   .defaultIdTomo  if task.is_tomo == true
   .defaultIdNonTomo if task.is_tomo == false
③ properties.movieProcessDefinitionId                ← 旧的全局回退（仅 type=movie 时生效）
```

**Rationale:**
- 显式选择优先于约定优先于全局，符合最小惊讶原则。
- 把 `is_tomo` 维度做到默认值层而非字段层，前端"每个 type 一个 Select"足够，不需要用户手动维护 tomo/non-tomo 双套字段。
- 第 ③ 段仅对 `type=movie` 生效，确保未升级配置的环境零变更继续工作；type=mdoc 没有旧回退即报错（fail-fast，保护 tomo 任务不被误启动到 movie 流程）。

**Alternatives considered:**
- 仅两段（用户选择 vs 全局回退）：丢失 `is_tomo` 智能默认，对 tomo 数据集创建任务的体验差。
- type+is_tomo 直接落到 Task 字段（`Task.movieTomoProcessDefinitionId`）：字段爆炸，且不符合"用户视图"——用户只关心"这个 task 用哪个 movie 流程"。

### Decision 4: Task 模型按 type 拆字段（方案 P）

**Choice:** `Task.movieProcessDefinitionId`（已有，去 `@Hidden`） + 新增 `Task.mdocProcessDefinitionId`。Map 形式 `Task.processDefinitionIds: Map<String,String>` 被否决。

**Rationale:**
- 与现有 `MovieKiwiWorkflowService.resolveMovieBpmProcessId` 直接对齐，零重构。
- Mongo 文档强类型字段对查询与索引更友好，未来加 type 时增加字段即可。
- 老文档无该字段时按 `null` 处理，自动走默认值层。

### Decision 5: Mdoc Workflow Select 的可见性规则

**Choice:** Movie Workflow Select 始终显示；Mdoc Workflow Select 仅当 `taskSettings.isTomo.value === 'true'` 时显示。

**Rationale:**
- 在 cryoEM 业务里，tomo 数据集**同时**需要 movie 处理（每帧）和 mdoc 处理（倾斜系列元数据），两者并行存在。
- non-tomo 数据集没有 mdoc 概念，避免在 UI 上引入无效字段。
- `is_tomo` 由 `DatasetSelector` 选择数据集后决定，已经只读、无歧义。

**Alternatives considered:**
- 始终显示两个 Select：non-tomo 时 mdoc 必填会困住用户。
- 互斥（tomo 只显 mdoc）：丢失 movie 流程，与现有引擎链路冲突。

### Decision 6: UI 控件采用简单 Select

**Choice:** 复用 `LabeledInputRow` + `select native` 风格，与 Acquisition Parameters 区的 `Is Tomo` 选择器一致（参见 `GlobalSettings.tsx` 已有写法）。

**Rationale:**
- 流程数量预期较少（<20）。
- 视觉一致，不引入新组件依赖。
- 选项展示 `name` (来自 BpmProcess)，`value` 为 BpmProcess id；不展示版本号（用最新已部署版本）。

**Alternatives considered:**
- Autocomplete：流程数大时合适，本次不必要。
- 卡片式：占位过多。

### Decision 7: 创建模式必填，编辑模式禁用

**Choice:** `status === 'create'` 时 Movie Workflow Select 必填；`is_tomo === 'true'` 时 Mdoc Workflow 也必填。`status === 'edit'` 时两个 Select 均显示当前值但 `disabled`。

**Rationale:**
- Task 一旦启动，已经有 Movie/Mdoc 实例运行在所选 BpmProcess 上，更换会破坏状态一致性。
- 与 Task Name、Task Directory 等"创建后不可改"字段保持一致。

### Decision 8: 配置形态

**Choice:** 在 `KiwiWorkflowProperties` 下新增：

```yaml
app:
  kiwi:
    workflow:
      process-types:
        movie:
          project-id: <kiwi-bpm-project-id>
          default-id-non-tomo: <bpm-process-id>
          default-id-tomo: <bpm-process-id>
        mdoc:
          project-id: <kiwi-bpm-project-id>
          default-id-non-tomo: ""        # mdoc 通常无 non-tomo，留空表示该组合无默认
          default-id-tomo: <bpm-process-id>
```

**Rationale:**
- 与现有 `KiwiWorkflowProperties` 同前缀，集中维护。
- 默认空 `Map`，未配置即等价于本变更不存在（向后兼容）。

## Risks / Trade-offs

- **[风险] 用户漏配 process-types 但前端已升级** → 代理 API 返回空清单 → Select 没有选项 → 创建任务被卡住。**缓解**：cryo-web 启动时记录 `WARN`，前端在空清单时给出 toast 提示"请联系管理员配置 process-types.<type>"，并同步把 `applicationContext` 文档加到 `application.yml` 注释里。

- **[风险] Kiwi 端漏勾选 entry，新建流程默认 entry=false** → 列表为空。**缓解**：`BpmProcess.entry` 默认 `false` 是安全侧设计；BPM 设计器流程属性面板增加复选框并在新建流程的提示文案中说明"勾选后才会出现在 cryoEMS 等下游系统的流程选择列表"。

- **[风险] 全局回退 `app.kiwi.workflow.movie-process-definition-id` 与 type 默认值同时存在时优先级歧义** → 解析时按 Decision 3 顺序执行；并在 `MovieKiwiWorkflowService` 添加 `DEBUG` 日志打印命中的层级与 BpmProcess id，便于排查。

- **[风险] mdoc Service 占位，但不实际启动流程** → 本变更只确保 `Task.mdocProcessDefinitionId` 正确写入与 `MdocKiwiWorkflowService.resolveMdocBpmProcessId` 可被复用；mdoc 真实编排链路（mdoc 引擎）由后续变更接管。**缓解**：tasks.md 明确 mdoc 仅做"选择 + 持久化 + 解析"三件事，运行链路标注为 out-of-scope。

- **[风险] Task 字段去 `@Hidden` 后影响其他客户端**（如 admin 后台用脚本 POST `/api/task`） → 本质上字段一直可写，只是文档层面变化；不破坏现有调用。

- **[风险] BpmProcess.entry 字段对历史 Camunda 流程未启动产生影响**：默认 false → 已存在的"实际是入口"的流程在升级后不会出现在新查询端点中。**缓解**：在迁移说明中要求管理员对每个被 cryoEMS 使用的入口流程手工勾选 `entry=true`。原 `GET /bpm/process` 通用列表 API 不变，仍能看到所有流程。

## Migration Plan

部署顺序：先 `kiwi-admin`，再 `cryo-em-server-backend`，最后 `cryo-em-server-frontend`。各阶段都对老调用方/老配置零破坏。

1. **kiwi-admin**：发布带 `BpmProcess.entry` 字段与 `GET /bpm/process/entries` 端点的版本；管理员在 BPM 设计器中把已部署且需要被 cryoEMS 使用的"入口流程"逐个勾选 `entry=true`。
2. **cryo-em-server-backend**：发布带 `process-types` 配置位、`/api/bpm/processes` 代理、`Task.mdocProcessDefinitionId`、三段回退解析的版本；管理员按物理部署在 `application.yml` 中配置 `process-types.movie/mdoc`。**未配置时旧 `movie-process-definition-id` 仍生效**。
3. **cryo-em-server-frontend**：发布带 Workflow / Mdoc Workflow Select 的版本；用户从此可见可选。

回滚策略：
- 前端回滚到上一版即恢复"无选择框"，新建任务回退到全局默认。
- 后端回滚到上一版即关闭 `/api/bpm/processes` 与 `mdocProcessDefinitionId`；前端列表加载会失败，但已经创建的 Task 仍能用现有字段启动。
- Kiwi 回滚到上一版会丢失 `entry` 字段（Mongo 文档保留，下次升级回来仍可用），新查询端点消失，cryo-web 列表 API 报错降级。

## Open Questions

- 是否需要在 cryo-web `application.yml` 模板中给出"如何获取 projectId 与 process id"的步骤说明？建议在 `application.yml` 顶部注释里贴一条短链接到 Kiwi BPM 设计器路径，但不阻塞本变更。
- BPM 设计器流程属性面板的"入口流程"复选框应放在哪一组（基础信息 / 部署 / 高级）？设计阶段不细化，留给 kiwi-admin 前端实现时按现有面板布局自行选择最合理位置。
