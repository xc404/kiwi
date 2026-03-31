## Context

`BpmComponentService` 在 `afterPropertiesSet` 中于 `autoDeploy` 为真时调用各 `BpmComponentProvider#deploy`，当前实现始终 `saveAll` 全量组件。组件元数据来自类路径扫描（`ComponentUtils.fromClass` + `@ComponentDescription`），已包含 `version` 等字段，但此前未用于跳过未变更的写入。

## Goals / Non-Goals

**Goals:**

- 在自动部署路径中，仅当组件**定义相对上次持久化结果发生变化**时才写库。
- 指纹计算**稳定、可复现**（同一定义多次计算结果相同）。
- `version` 与参数、名称等一并纳入指纹，避免「只改参数却未升版本」时仍错误跳过。

**Non-Goals:**

- 不改变 `deleteNotExist` 的语义（仍按 `source` 下 key 删除提供者不再提供的组件）。
- 不引入新的外部依赖；指纹算法使用 JDK `MessageDigest`（SHA-256）。
- 不在此变更中解决 `saveAll` 对审计字段的合并策略（沿用既有行为）。

## Decisions

1. **持久化字段名**：在 `BpmComponent` 上新增 `deploymentSignature`（`String`，存 SHA-256 十六进制）。与业务 `version`（展示/注解）分离：前者为**计算指纹**，后者仍来自 `@ComponentDescription`。
2. **指纹输入范围**：参与计算的字段包括：`parentId`、`key`、`source`、`name`、`description`、`group`、`type`、`version`、输入/输出参数列表（按 `key` 排序后对每个 `BpmComponentParameter` 的稳定字段拼接）。**不包含** `id` 与 `BaseEntity` 审计字段，避免无意义变动。
3. **比较策略**：对 `deploy` 中每个待写入组件计算指纹；若 Mongo 中同 `id` 文档存在且 `deploymentSignature` 与计算值相等，则**跳过**该条 `save`。若库中无指纹（历史数据），视为不相等，执行一次写入以补全指纹。
4. **手动 `deployComponent`**：保存前设置 `deploymentSignature`，保证与自动路径一致。

## Risks / Trade-offs

- **[Risk]** 指纹字段遗漏未来新增的重要模型字段 → **Mitigation**：新增影响设计器/运行时的字段时，同步将其实入指纹计算逻辑（代码审查与规格 `bpm-component-auto-deploy` 约束）。
- **[Trade-off]** 全量序列化细节变更（如拼接顺序）会导致一次性重写 → 可接受；属预期内的「定义变更」。

## Migration Plan

- 无单独迁移脚本：首次带新代码启动时，旧文档无 `deploymentSignature` 会触发写入并写入新字段。
- 回滚：回退代码后 Mongo 中多出的字段可被旧版本忽略；行为恢复为每次全量 `saveAll`。

## Open Questions

- 无。
