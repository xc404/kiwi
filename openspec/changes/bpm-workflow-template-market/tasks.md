# Tasks — BPM 流程模板 Market

> **说明**：C1/C2 已在实现阶段完成；本 tasks 用于 OpenSpec 追溯与 C3/后续项跟踪。

## 1. C1 — 数据模型与 DAO

- [x] 1.1 `BpmTemplatePack` / `BpmTemplateProcess` / `BpmTemplateEnvVar` / `BpmTemplatePackManifest` 实体
- [x] 1.2 `BpmTemplatePackDao`、`BpmTemplateProcessDao`、`BpmTemplateEnvVarDao`
- [x] 1.3 DTO：`BpmTemplatePackDetailDto`、`PublishTemplatePackInput`、`InstallTemplatePackInput`、`InstallTemplatePackResult`

## 2. C1 — 服务与 API

- [x] 2.1 `BpmTemplatePackService`（分页、详情、可见性）
- [x] 2.2 `BpmTemplatePackPublishService`（`publishProject` / `publishProcess` / `publishFromBundle`）
- [x] 2.3 `BpmTemplatePackInstallService`（`installPack` / `installPackInto` / `installProcess` + CallActivity 重映射）
- [x] 2.4 `BpmTemplatePackManifestScanner`
- [x] 2.5 `BpmTemplatePackCtl`（`bpmMarket_*` 全套 + `bpmMarket_aiPage`）
- [x] 2.6 `BpmProjectCtl` 扩展（`bpmProj_exportAsTemplate` 等别名）

## 3. C1 — 菜单

- [x] 3.1 `R__SysMenu.json` 增加「模板市场」

## 4. C1 — 前端

- [x] 4.1 `/bpm/market` 列表页（`bpm-market.ts`）
- [x] 4.2 `/bpm/market/:packId` 详情页（`bpm-market-detail.ts`）
- [x] 4.3 导出/安装/导入模态框组件
- [x] 4.4 `bpm-project.ts` / `bpm-project-process.ts` 集成
- [x] 4.5 `bpm-routing.ts` 路由注册

## 5. C2 — 文件包

- [x] 5.1 `BpmTemplatePackBundleService`（zip 构建与解析）
- [x] 5.2 `bpmMarket_exportPack` / `exportProject` / `importPack` / `importAndInstall`
- [x] 5.3 SHA-256 checksum 写入 `BpmTemplatePack.checksum`

## 6. 质量与联调（建议）

- [ ] 6.1 端到端：发布项目 → 市场可见 → installPack → 验证多流程 + env + CallActivity
- [ ] 6.2 详情页下载改为带 Token 的 blob 下载
- [ ] 6.3 项目流程页「导入模板」改为市场选择器（替代 prompt）
- [ ] 6.4 安装前 `requiredComponentKeys` 缺失组件警告或拦截

## 7. C3 — 公网 Registry（后续 change）

- [ ] 7.1 Registry 服务与实例 sync API
- [ ] 7.2 审核流（`PendingReview` → `Published`）
- [ ] 7.3 GPG `SIGNATURE` 文件与 trustKeys 校验

## 8. AI 集成（后续，见 ai 场景生图 plan）

- [ ] 8.1 `applyWorkflowTemplate` ClientAction
- [ ] 8.2 AI prompt 增补：检索模板 → installPack → 微调

## 9. 归档

- [ ] 9.1 完成 6.x 联调后执行 `/opsx:archive`
