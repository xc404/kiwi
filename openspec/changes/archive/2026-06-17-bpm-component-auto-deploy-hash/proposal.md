## Why

当前 `BpmComponentService` 在 `bpm.component.auto-deploy=true` 时，启动阶段会对每个 `BpmComponentProvider` 返回的组件**无条件** `saveAll` 写入 MongoDB，即使元数据未变化。这会产生不必要的数据库写、更新时间与无意义的文档刷新。

## What Changes

- 为每个 BPM 组件引入**部署指纹**（基于元数据的 SHA-256 十六进制字符串），持久化到 MongoDB。
- 自动部署路径（`deploy(BpmComponentProvider)`）在写入前将**计算出的指纹**与库中已有文档的指纹比较；**仅当指纹不一致**（新组件或定义变更）时才执行保存。
- 注解 `@ComponentDescription#version` 等字段参与指纹计算，因此可通过**显式版本号**或**参数/描述等变更导致的哈希变化**触发部署。
- 手动 `deployComponent` 仍写入单条组件，并在保存前刷新指纹，保证与自动部署规则一致。

## Capabilities

### New Capabilities

- `bpm-component-auto-deploy`: 定义 BPM 组件自动部署时基于部署指纹（含版本在内的元数据哈希）的增量写入行为。

### Modified Capabilities

- （无；`openspec/specs/` 下暂无存量规格。）

## Impact

- **后端**：`BpmComponent` 模型、`BpmComponentService.deploy`、`deployComponent`；新增部署指纹计算工具类。
- **配置**：保留 `bpm.component.auto-deploy`；无需新增必选配置项。
- **数据**：已有文档缺少指纹字段时视为「需部署」，首次启动会补写指纹（与此前全量写入相比，后续重启写放大显著降低）。
- **API**：列表/详情 JSON 可能多出一个可选字段 `deploymentSignature`，前端可忽略。
