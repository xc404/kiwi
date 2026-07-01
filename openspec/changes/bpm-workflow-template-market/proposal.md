## Why

Kiwi 工作流目前缺少可发现、可复用、可分享的流程起点：用户需从零画 BPMN，或手工复制项目。将「流程模板」升级为 **Template Market**（售卖单位为 **模板包 `BpmTemplatePack`**，对标 `BpmProject`），可支持单流程与多流程解决方案的发现、发布、安装与跨实例文件分发，并为后续 AI 场景生图、公网 Registry 提供统一契约。

## What Changes

- **C1（站内 MVP）**：MongoDB 实体 `BpmTemplatePack` / `BpmTemplateProcess` / `BpmTemplateEnvVar` + manifest 扫描；`bpmMarket_*` REST API；`installPack` / `installPackInto` / `installProcess`；CallActivity `calledElement` 重映射；项目侧导出/导入 UI。
- **C2（文件包）**：`.kiwi-template-pack` zip 导出/导入（`manifest.json`、`processes/*.bpmn`、`env-vars.json`）；SHA-256 checksum 写入包元数据。
- **C3（后续）**：公网 Registry、审核流、GPG 签名与 trustKeys 校验（本 change 仅规划，不实现）。
- **AI 集成（后续）**：`bpmMarket_aiPage` 已预留；`applyWorkflowTemplate` ClientAction 与 prompt 增补待独立任务。

## Capabilities

### New Capabilities

- `bpm-template-market`：模板包数据模型、Market API、发布/安装语义、`.kiwi-template-pack` 文件格式、前端市场页与项目域集成。

### Modified Capabilities

- （无。不修改 `openspec/specs/` 下既有 capability 的 normative 行为。）

## Impact

- **后端**：`com.kiwi.project.bpm.model`（新实体）、`dao`、`service`（Publish/Install/Bundle/ManifestScanner）、`BpmTemplatePackCtl`、`BpmProjectCtl` 扩展。
- **前端**：`/bpm/market`、`/bpm/market/:packId`；项目/项目流程页导出与导入模态框；`R__SysMenu.json` 菜单。
- **MCP**：`bpmMarket_*` operationId 自动注册为 AI 工具（含 `bpmMarket_aiPage`）。
- **依赖**：无新外部依赖；复用 `BpmProcessDefinitionService.syncBpmnIdentity`、现有 Mongo 与 Sa-Token 鉴权。

## 非目标

- C3 公网 Registry 与审核工作流
- 安装前对 `requiredComponentKeys` 的强拦截（当前仅 manifest 扫描记录）
- GPG `SIGNATURE` 文件与 trustKeys（C3）
- AI Planner 完整集成（见 `ai_场景生图优化` plan）
