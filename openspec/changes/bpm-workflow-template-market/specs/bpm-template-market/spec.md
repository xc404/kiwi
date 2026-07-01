## ADDED Requirements

### Requirement: 模板包数据模型

系统 SHALL 在 MongoDB 中维护模板包及其子资源，结构与用户工作区 `BpmProject` 对标：

- `BpmTemplatePack`：listing（`name`、`slug`、`kind`、`status`、`visibility`、`manifest`、`version` 等）
- `BpmTemplateProcess`：`packId` + `processKey` + `bpmnXml`（可含 `entry` 标记）
- `BpmTemplateEnvVar`：`packId` + 环境变量字段

`kind` SHALL 为 `Single`（单流程）或 `Solution`（多流程）。

#### Scenario: 单流程包

- **WHEN** 用户从单条 `BpmProcess` 发布模板
- **THEN** 系统 SHALL 创建 `kind=Single` 的 `BpmTemplatePack` 且仅含一条 `BpmTemplateProcess`

#### Scenario: 多流程解决方案包

- **WHEN** 用户从整个 `BpmProject` 发布模板
- **THEN** 系统 SHALL 创建 `kind=Solution` 的包，包含项目下全部流程与环境变量

### Requirement: 模板包可见性与列表

系统 SHALL 按 `visibility`（`Private` / `Org` / `Public`）与当前登录用户过滤可读模板包。分页列表 API `bpmMarket_page` SHALL 返回当前用户可见的 `BpmTemplatePack` 分页结果。

#### Scenario: 公开包对所有登录用户可见

- **WHEN** 模板包 `visibility=Public` 且 `status=Published`
- **THEN** 任意已登录用户 SHALL 能在 `bpmMarket_page` 结果中看到该包

#### Scenario: 私有包仅发布者可见

- **WHEN** 模板包 `visibility=Private`
- **THEN** 仅 `publisherId` 与系统管理员（若实现）SHALL 能读取详情

### Requirement: 发布模板包

系统 SHALL 提供从工作区发布模板包的 API：

- `bpmMarket_publishProject`：快照 `BpmProject` 下全部流程与环境变量
- `bpmMarket_publishProcess`：从单流程发布 `kind=Single` 包

发布时 SHALL 扫描 BPMN 生成 `BpmTemplatePackManifest`（含 `requiredComponentKeys`、`callActivityBindings`）。

#### Scenario: 从项目发布

- **WHEN** 项目所有者调用 `POST /bpm/market/publish/project/{projectId}` 并提交元数据（名称、摘要、可见性等）
- **THEN** 系统 SHALL 持久化新的 `BpmTemplatePack` 及关联流程与环境变量

### Requirement: 安装模板包

系统 SHALL 支持以下安装模式：

| operationId | 行为 |
|-------------|------|
| `bpmMarket_installPack` | 新建 `BpmProject` 并复制包内全部流程与环境变量 |
| `bpmMarket_installPackInto` | 复制到已有 `targetProjectId` |
| `bpmMarket_installProcess` | 仅安装包内指定 `processKey` 到目标项目 |

安装时 SHALL：

1. 将包内 `processKey` 映射为新 `BpmProcess.id`
2. 重写 BPMN 中 CallActivity 的 `calledElement` 以指向新 id
3. 对新流程调用 `syncBpmnIdentity`
4. 复制 `BpmTemplateEnvVar` 为 `BpmProjectEnvVar`

#### Scenario: 安装为新项目

- **WHEN** 用户调用 `POST /bpm/market/{packId}/install`
- **THEN** 系统 SHALL 创建新 `BpmProject`，复制全部模板流程与环境变量，并返回 `projectId` 与 `processCount`

#### Scenario: CallActivity 重映射

- **WHEN** 模板包内流程 A 通过 CallActivity 引用流程 B 的 `processKey`
- **THEN** 安装后流程 A 的 BPMN 中 `calledElement` SHALL 指向新创建的流程 B 的 `BpmProcess.id`

### Requirement: 模板包文件格式（C2）

系统 SHALL 支持 `.kiwi-template-pack`（zip）导出与导入，至少包含：

- `manifest.json`
- `README.md`
- `env-vars.json`
- `processes/{processKey}.bpmn`

导出 API：`bpmMarket_exportPack`、`bpmMarket_exportProject`。导入 API：`bpmMarket_importPack`、`bpmMarket_importAndInstall`。包 SHALL 计算并记录 SHA-256 `checksum`。

#### Scenario: 导出并再导入

- **WHEN** 用户下载 `bpmMarket_exportPack` 生成的 zip 并调用 `bpmMarket_importPack`
- **THEN** 系统 SHALL 在站内创建等价的 `BpmTemplatePack` 及子资源

#### Scenario: 导入并安装

- **WHEN** 用户上传 zip 至 `bpmMarket_importAndInstall` 并可选指定 `projectName`
- **THEN** 系统 SHALL 导入包并执行 `installPack`，返回新项目 id

### Requirement: 项目域别名 API

`BpmProjectCtl` SHALL 提供项目侧便捷入口，语义与 Market API 等价：

- `bpmProj_exportAsTemplate` → 发布为模板包
- `bpmProj_importTemplatePack` / `bpmProj_importTemplatePackInto` → 安装
- `bpmProj_exportTemplateFile` → 导出 zip

#### Scenario: 项目页导出为模板

- **WHEN** 项目所有者调用 `POST /bpm/project/{id}/export-as-template`
- **THEN** 系统 SHALL 创建模板包并返回 `BpmTemplatePack`

### Requirement: 前端模板市场页面

前端 SHALL 提供：

- `/bpm/market`：模板包列表（对接 `bpmMarket_page`）
- `/bpm/market/:packId`：详情、安装为新项目、下载 zip
- 项目管理与项目流程页：导出为模板、导入模板包入口
- 系统菜单项「模板市场」

#### Scenario: 从市场安装

- **WHEN** 用户在详情页点击「安装为新项目」
- **THEN** 前端 SHALL 调用 `bpmMarket_installPack` 并导航至新项目或提示成功

### Requirement: AI 分页检索（预留）

系统 SHALL 提供 `bpmMarket_aiPage`（`GET /bpm/market/search/ai-page`），接受 `keyword`、`category`、`tag`、`kind`、`page`（从 0）、`size`（默认 20，最大 100），供 MCP/AI 工具检索模板包。

#### Scenario: MCP 工具检索

- **WHEN** AI 助手调用 `bpmMarket_aiPage` 并传入 keyword
- **THEN** 系统 SHALL 返回与 `bpmMarket_page` 相同可见性规则下的分页结果
