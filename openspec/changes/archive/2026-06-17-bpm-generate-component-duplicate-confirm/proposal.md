## Why

从 CLI help 或 OpenAPI 批量生成 BPM 组件并保存时，若库中已存在「同一来源语义」的组件，直接写入会产生重复或静默覆盖风险。需要在保存前检测冲突，并按用户对每条草稿的选择执行取消、覆盖或新增。

现有字段 **`key` 已承担其它业务语义**，不宜再作为本次生成入库的判重键；因此新增 **`sourceKey`**，专门表示「生成来源维度上的组件标识」（如 CLI 前缀+子命令、OpenAPI 的 method+operationId 等），用于冲突检测与本次实现。

## What Changes

- 在 **`BpmComponent` 上新增字段 `sourceKey`**（字符串，可为空；生成管线写入、冲突逻辑读写）。
- 在「生成并保存」流程（CLI help 与 OpenAPI 两条入口共用）中，于持久化前按 **`parentId` + `sourceKey`** 查询是否与已有组件冲突（不使用 `key` 判重）。
- 若存在冲突：针对每条冲突展示选项——**取消**（本条不保存）、**覆盖**（用草稿更新已有文档）、**新增**（仍插入一条新组件；通过生成新的唯一 **`sourceKey`** 避免再次冲突，**不改变既有业务 `key` 的既有用途**）。
- 若用户在某一步选择全盘取消或关闭向导：中止剩余保存操作（具体粒度在设计中细化）。
- 后端可提供按 **`parentId` + `sourceKey`** 查询是否存在的 API；或与批量预检接口合并（在设计中选定）。
- 前端：抽取通用的「待保存组件列表 → 冲突检测 → 逐条/批量确认 → 调用 PUT/POST」流程，供 `confirmGenerateFromCli` 与 `confirmGenerateFromOpenApi` 复用；展示冲突时可显示 `sourceKey` 摘要。

## Capabilities

### New Capabilities

- `bpm-component-generate-save-conflict`: 定义基于 `sourceKey` 的冲突检测规则、用户可选动作（取消 / 覆盖 / 新增）及与各 API 的对应关系。

### Modified Capabilities

- （无现有 BPM 组件相关 spec；本次仅新增能力。）

## Impact

- **数据模型**：MongoDB `BpmComponent` 增加 `sourceKey`；存量文档可为空；**CLI/OpenAPI 生成器**须为每条草稿填充稳定的 `sourceKey`。
- **后端**：`BpmComponent` 模型、`BpmComponentCtl`、`BpmComponentDao`/`BpmComponentService`（按 **parentId + sourceKey** 查询）；`CliHelpParser` / `OpenApiComponentGenerator` 等生成路径写入 `sourceKey`。
- **前端**：`bpm-component.ts`（及模板）中 CLI/OpenAPI 确认逻辑与冲突 UI 使用 `sourceKey`；**不得**用 `key` 做本次判重。
