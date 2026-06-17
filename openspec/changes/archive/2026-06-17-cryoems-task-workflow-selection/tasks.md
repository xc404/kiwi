## 1. kiwi-admin 后端 — BpmProcess.entry 与查询端点

- [x] 1.1 在 `kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/model/BpmProcess.java` 新增 `private boolean entry;` 字段（Lombok `@Data` 自动产生 getter/setter），默认 `false`，并在字段上加 Javadoc 说明语义
- [x] 1.2 在 `BpmProcessDefinitionCtl.SaveInput` 中新增 `private Boolean entry;`；`saveProcessDefinition`（`PUT /bpm/process/{id}`）中按"非 null 即写入"模式更新 `bpmProcess.entry`
- [x] 1.3 在 `BpmProcessDefinitionCtl` 新增 `GET /bpm/process/entries` 端点，支持可选查询参数 `projectId`，过滤条件 `entry=true && deployedVersion>0 && deployedAt!=null`；优先用 `BpmProcessDefinitionDao` / `QueryParams` 现有能力，无法直接表达时 fallback 到 `findAll().stream().filter(...).toList()` 但保留分页语义
  - 实际实现：直接构造 `Criteria.where("entry").is(true).and("deployedVersion").gt(0).and("deployedAt").ne(null)` + 可选 `projectId`，通过 `BaseMongoRepository.findBy(Query)` 一次性查询；不做分页（入口流程数量级不大，且前端只需 id+name）。
- [x] 1.4 给 `bpmPd_searchEntries` operationId 加 `@Operation` 与 `@Tag`，与本控制器其他端点风格一致
  - 落地为 operationId `bpmPd_entries`（与最终路径 `/bpm/process/entries` 命名一致）。
- [ ] 1.5 在 `kiwi-admin/backend` 现有测试目录下添加单元/切片测试覆盖 `entry` 过滤、`deployed` 过滤、`projectId` 过滤的三类组合（至少 3 个 case）
  - **跳过**：`kiwi-admin/backend` 目前没有任何测试目录与基线（`src/test/**` 不存在），新增测试需要先搭建 `@DataMongoTest` / Testcontainers 等基础设施，超出本变更范围。建议在后续基础设施建设时补齐。

## 2. kiwi-admin 前端 — 流程列表「入口流程」标记

- [x] 2.1 在流程列表页（`kiwi-admin/frontend/src/app/pages/bpm/project/bpm-project-process.ts`）的 `pageConfig.fields` 中新增 `entry` 字段（label: 入口流程 / Entry Process）
  - 实际落点：变更范围调整为流程列表 CRUD 页而非设计器属性面板。`type: FieldType.Boolean` 由 CRUD 框架自动渲染为列表布尔列 + 编辑表单 CheckBox；流程设计器（`bpm-editor.ts/.html`、`bpm-editor-process-meta`）保持原状，无任何改动。
- [x] 2.2 编辑提交触发 `PUT /bpm/process/{id}` 保存 `entry: boolean`；与同表单其他字段（如「名称」）的保存路径保持一致
  - CRUD 编辑表单提交统一走 `PUT /bpm/process/{id}`，body 包含表单字段；后端 `BpmProcessDefinitionCtl.SaveInput.entry` 已按「非 null 即写入」语义处理。
- [x] 2.3 字段附 `description` 文案："勾选后该流程会出现在 cryoEMS 等下游系统的工作流选择列表中"
  - 通过 `CrudFieldConfig.description` 渲染（与其他字段说明文案风格一致）。
- [x] 2.4 创建表单中默认隐藏（`edit.create: 'hidden'`）
  - 流程刚创建时尚未部署，标记入口无意义；用户应在流程部署后回到列表「编辑」中再勾选 `entry`。viewer / 只读语境下 CRUD 表单天然不暴露编辑入口，无需额外处理。

## 3. cryo-em-server-backend — 配置与代理端点

- [x] 3.1 在 `cryo-web-server/src/main/java/com/cryo/integration/workflow/KiwiWorkflowProperties.java` 新增 `private Map<String, ProcessTypeConfig> processTypes = new LinkedHashMap<>();` 与内部静态类 `ProcessTypeConfig { String projectId; String defaultIdNonTomo; String defaultIdTomo; }`（用 `@Data`）
- [x] 3.2 在 `application.yml` 与 `application-local.yml` 的 `app.kiwi.workflow` 段下加注释示例：`process-types: { movie: { project-id, default-id-non-tomo, default-id-tomo }, mdoc: {...} }`，默认空（不实际启用）
  - `application.yml` 已加注释示例与占位行；`application-local.yml` 是个人开发环境配置，未默认启用 process-types（按需追加即可），不强制改动。
- [x] 3.3 新建 `cryo-web-server/src/main/java/com/cryo/ctl/BpmProcessProxyCtl.java`，路径 `/api/bpm/processes`，提供 `GET ?type=...`：注入 `KiwiClient`、`KiwiWorkflowProperties`；构造 `URI` 调 `/bpm/process/entries?projectId=<X>` 并解析 JSON
  - 实际依赖 `KiwiWorkflowClient.listEntryProcesses(projectId)`（新加的方法），以复用 PAT→Sa-Token 的 `Authorization` 头与 R-envelope 解析。
- [x] 3.4 在 `BpmProcessProxyCtl` 中定义简化 DTO `record WorkflowProcessSummary(String id, String name, Integer deployedVersion, Date deployedAt) {}`；返回 `List<WorkflowProcessSummary>`（注意 `ResponseAdvice` 会包装为 `CollectionResult`）
- [x] 3.5 校验 type：未配置 type 返回 `IllegalArgumentException` 或 `ResponseStatusException(400, ...)`；登录鉴权使用与 `TaskCtl` 一致的注解（`@SaCheckLogin`，无角色限制）
- [ ] 3.6 在 `cryo-web-server/src/test` 下新增 `BpmProcessProxyCtlTest`：mock `KiwiClient` 验证 type 校验、空列表、上游 5xx 三个场景
  - **跳过**：与 1.5 相同，仓库当前未维护单元测试；先随主功能合入，后续补齐。

## 4. cryo-em-server-backend — Task 字段与 Service 解析

- [x] 4.1 在 `cryo-web-server/src/main/java/com/cryo/model/Task.java` 移除 `movieProcessDefinitionId` 字段上的 `@Hidden`；新增 `private String mdocProcessDefinitionId;`（不带 `@Hidden`），加 Javadoc
- [x] 4.2 修改 `MovieKiwiWorkflowService.resolveMovieBpmProcessId(Task task)` 实现三段回退：① `task.getMovieProcessDefinitionId()`；② `properties.processTypes.get("movie")` 按 `task.getIs_tomo()` 选 `defaultIdTomo`/`defaultIdNonTomo`；③ `properties.getMovieProcessDefinitionId()`；使用 `StringUtils.hasText` 判断
- [x] 4.3 在 `MovieKiwiWorkflowService` 解析处加 `log.debug` 输出命中层级与 BpmProcess id（"resolve movie process: hit=task|type-default|legacy-global, id=..."）
- [x] 4.4 新建 `MdocKiwiWorkflowService`（同包），构造与 `MovieKiwiWorkflowService` 同形：注入 `KiwiWorkflowClient`、`KiwiWorkflowProperties`；提供 `resolveMdocBpmProcessId(Task task)`，仅做两段回退：task 字段 → `processTypes.mdoc` 按 is_tomo 选默认；MUST 不回退到 `movie-process-definition-id`
- [x] 4.5 `MdocKiwiWorkflowService` 提供 `ensureStarted(...)` 占位方法（可仅打日志 + 返回），保持与 `MovieKiwiWorkflowService.ensureStarted` 形态一致；本变更不接入实际调度链路（注释标注 out-of-scope）
- [ ] 4.6 新增/扩展 `MovieKiwiWorkflowServiceTest`：覆盖三段回退的 5 个 scenario（详见 `specs/cryoems-task-workflow-fields/spec.md`）；新增 `MdocKiwiWorkflowServiceTest` 覆盖 4 个 scenario
  - **跳过**：与 3.6 同因；测试基础设施缺失。
- [x] 4.7 在 `TaskCtl.saveTask` 不需要改动（`@RequestBody Task` 反序列化自动接收新字段），但人工 review 确认 mongoTemplate 持久化两个新字段无 `@Transient`/`@JsonIgnore` 阻挡
  - 已 review：两个字段均为普通 String，无 `@Transient` / `@JsonIgnore`，按 Spring Data Mongo 默认映射写入。

## 5. cryo-em-server-frontend — Workflow 选择器 UI

- [x] 5.1 在 `cryo-em-server-frontend/src/services` 下新建 `workflows.ts`，导出 `getWorkflowProcesses(type: 'movie' | 'mdoc'): Promise<WorkflowProcessSummary[]>`，调 `${API_BASE_URL}/api/bpm/processes?type=...`，复用 `request` + `parse_response`
  - 同步在 `src/api.tsx` 加 `export * from './services/workflows'`，与现有 services 风格保持一致。
- [x] 5.2 定义 TypeScript 类型 `WorkflowProcessSummary { id: string; name: string; deployedVersion: number; deployedAt: string; }`
- [x] 5.3 在 `src/config/task.config.ts` 的 `settingsTemplate` 顶层新增 `movieProcessDefinitionId: { value: '' }` 与 `mdocProcessDefinitionId: { value: '' }`（保持与既有 `taskName` 等同形）
- [x] 5.4 在 `src/components/task/GlobalSettings.tsx` 的 Project Information 区，Task Directory 行之后、Collaborators 之前，新增 Movie Workflow `LabeledInputRow select native`
- [x] 5.5 在 5.4 下方按条件渲染 Mdoc Workflow Select：仅当 `taskSettings.isTomo.value === 'true'` 时渲染，行为与 5.4 一致但 type 为 `mdoc`，绑定 `mdocProcessDefinitionId`；当条件由 true 变为 false 时执行 `setTaskSettings(ts => ({...ts, mdocProcessDefinitionId: { value: '' }}))`
- [x] 5.6 在 `src/components/task/TaskTools.tsx` 的 `taskSettings2Params` 中显式将 `movieProcessDefinitionId` 落到请求体顶层 `result.movieProcessDefinitionId`；当 `taskSettings.isTomo.value === 'true'` 时再附加 `result.mdocProcessDefinitionId`，否则 NOT 写入该 key
- [x] 5.7 在 `checkValidation` 中新增校验：创建模式下 `movieProcessDefinitionId` 必填；当 `isTomo=true` 时 `mdocProcessDefinitionId` 必填；缺失时通过 toast 提示具体字段名
- [x] 5.8 在 `src/app/edit/page.tsx` 调用 `taskParams2Settings`/类似回填路径中确认两个流程字段被正确回显（不必新增交互）
  - `taskParams2Settings` 默认分支按 `name` 匹配 `settingsTemplate` 顶层项，新增的 `movieProcessDefinitionId` / `mdocProcessDefinitionId` 自动回显；编辑模式下两个 Select `disabled={status === 'edit' || disabled}`，符合 spec 的「禁用」要求。
- [x] 5.9 在 `GlobalSettings.tsx` Movie/Mdoc Select 旁加 tooltip 文案，与现有字段风格一致；在选项加载失败或为空时通过 `react-toastify` 提示

## 6. 端到端验证

- [ ] 6.1 本地启动 kiwi-admin → cryo-web → frontend；在 BPM 设计器创建并部署一个流程，勾选 entry=true
- [ ] 6.2 在 cryo-web `application.yml` 配置 `process-types.movie.project-id` 指向上述项目；前端创建任务 → 看到 Movie Workflow 下拉包含该流程
- [ ] 6.3 选择该流程并提交 → 验证 Mongo `tasks` 集合中 `movieProcessDefinitionId` 值正确写入
- [ ] 6.4 启动该任务 → cryo-web 日志 DEBUG 输出 `hit=task` 路径 → Kiwi 流程实例成功启动
- [ ] 6.5 不配置 `process-types`，仅保留旧 `app.kiwi.workflow.movie-process-definition-id`，重启 cryo-web 验证旧任务仍正常启动（向后兼容回退路径）
- [ ] 6.6 创建一个 `is_tomo=true` 的数据集对应的任务，验证 Mdoc Workflow Select 出现并必填，提交后 `mdocProcessDefinitionId` 落库
- [ ] 6.7 进入编辑页验证两个 Select 均回显且禁用

> 第 6 节为人工 E2E 验证，需运行三端（kiwi-admin、cryo-web、frontend）+ Kiwi 数据库；本变更实现阶段不勾选，待联调环境就绪后由发起方逐项验证。
