## Context

- 当前：CLI help 与 OpenAPI 生成后，前端分别调用 `POST /bpm/component` 落库；无冲突预检。
- 字段分工：**`key`** 继续承担既有流程/编辑侧业务含义；**不得**再作为「同一生成来源是否已入库」的判重依据。
- 新增 **`sourceKey`**：仅用于标识「本次生成场景下是否为同一条逻辑组件」（含冲突检测、覆盖/新增分支时的唯一性约束）。模型：`BpmComponent` 增加 `sourceKey`，与 `parentId` 组合界定同类生成项。

## Goals / Non-Goals

**Goals:**

- 在 `BpmComponent` 上落地 `sourceKey`，并由 CLI/OpenAPI 生成器写入稳定、可复现的值。
- 在保存生成结果前检测与库中已有组件的冲突：**`(parentId, sourceKey)`** 一致则视为同一来源组件冲突。
- 对每条冲突提供 **取消**、**覆盖**、**新增**；**新增** 时对 **`sourceKey`** 做非破坏性调整（后缀、短 id 等），**不**为规避冲突随意改写业务 **`key`**（除非产品另有规则）。
- 两条入口共用同一套前端「预检 → 展示冲突 → 按选择落库」逻辑；后端提供批量预检 API。

**Non-Goals:**

- 不重新定义或迁移既有 `key` 的业务含义。
- 不要求合并三方 diff 式对比 UI（首版可展示名称、`sourceKey`、父级等摘要）。

## Decisions

1. **冲突定义**  
   - **已存在** 指：库中已有文档与待保存草稿的 **`parentId` 与 `sourceKey` 均一致**（空值按 null/空串统一规则与 DAO 查询一致）。  
   - **明确排除**：不使用 `key` 参与本流程的冲突判定。

2. **`sourceKey` 生成约定（实现时与生成器对齐）**  
   - CLI：例如 `helpCommand` 规范化 + 子命令/工具名等可稳定序列化串（具体由 `CliHelpParser` 实现并文档化）。  
   - OpenAPI：例如 `method + path + operationId`（或无 operationId 时的回退规则）拼成唯一语义串。  
   - 同一实现须保证：同一文档重复生成时 `sourceKey` 稳定，便于用户选择覆盖。

3. **预检 API**  
   - **推荐**：`POST /bpm/component/preview-conflicts`，请求体为待保存草稿列表（含 `parentId`、`sourceKey` 等），返回每条是否冲突及 `existingId`。  
   - 单条存在性查询可内部用 `findByParentIdAndSourceKey`（名称以代码为准）。

4. **用户动作语义**  
   - **取消**：该条不写入。  
   - **覆盖**：`PUT /bpm/component/{existingId}`，请求体为草稿并保留目标 id。  
   - **新增**：`POST`，为草稿生成新的 **唯一 `sourceKey`**（在未占用空间中），再预检或依赖单次生成策略；**业务 `key` 可按原草稿复制或由既有命名规则生成，与 sourceKey 解耦**。

5. **CLI 单条与 OpenAPI 多条**  
   - 同一数组管线：CLI 传入长度为 1 的数组。

## Risks / Trade-offs

- **[Risk]** 存量组件无 `sourceKey`，预检仅对新写入字段生效 → **Mitigation**：老数据不参与 sourceKey 冲突；新保存的组件均带 `sourceKey`。  
- **[Risk]** `sourceKey` 生成规则变更导致「同一条」判成两条 → **Mitigation**：规则变更写进生成器版本说明；必要时提供手工编辑 sourceKey（二期）。  
- **[Risk]** 并发双 POST 同 `sourceKey` → **Mitigation**：可选唯一复合索引 `(parentId, sourceKey)` + 重复键处理。

## Migration Plan

- 模型增加字段，旧文档 `sourceKey` 为空；新保存自生成器写入。  
- 回滚：去掉预检与字段使用；已写入的 `sourceKey` 可保留不影响旧逻辑。

## Open Questions

- `parentId` / `sourceKey` 空值参与匹配的精确规则与生成器空值行为对齐。  
- 「新增」时 `sourceKey` 后缀策略是否可配置。
