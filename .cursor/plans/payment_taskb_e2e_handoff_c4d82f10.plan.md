---
name: 支付模板 E2E 交接（任务 B）
overview: payment-integration-demo 作为 zip 捆绑 JAR 的黄金用例；前置 P0 已完成，可启动任务 B 实施。
todos:
  - id: verify-p0-export-baseline
    content: 空库启动后验证 payment-integration-demo 项目、5 流程、env 种子与 plugin JAR 加载
    status: pending
  - id: taskb-zip-format
    content: 按 模板包捆绑组件jar 计划实现 buildZip components/ 目录
    status: pending
  - id: taskb-golden-export
    content: 以本项目验收导出 zip 含 3 类 plugin JAR + manifest.requiredComponentKeys
    status: pending
  - id: taskb-import-e2e
    content: 无 payment 插件实例导入 zip → 冲突策略装 JAR → installPack → 设计器可打开
    status: pending
isProject: false
---

# 支付模板 E2E 交接（任务 B）

## 前置 P0 交付物（已完成）

| 交付物 | 路径 |
|--------|------|
| 支付插件模块 | `kiwi-bpmn/kiwi-bpmn-component-payment/` |
| 插件 JAR | `kiwi-admin/backend/plugins/kiwi-bpmn-component-payment-*-plugin.jar` |
| BPMN 源 | `kiwi-admin/backend/src/main/resources/bpm/payment-integration-demo/*.bpmn` |
| Mongo 种子 | `V20250701_001__BpmProjectPaymentDemo.json`、`002`（流程）、`003`（env） |
| 文档 | `docs/bpm-component.zh-CN.md`、`bpm/payment-integration-demo/README.md` |

## 黄金用例：payment-integration-demo

**项目 ID：** `payment-integration-demo`  
**名称：** 通用支付集成套件

### 流程清单

| processKey | entry | 职责 |
|------------|-------|------|
| `p-pay-main-001` | true | CallActivity 编排主流程 + Slack 通知 |
| `p-pay-create-001` | false | uuidGenerate + assignmentActivity |
| `p-pay-alipay-001` | false | plugin_paymentCreate (alipay) |
| `p-pay-wechat-001` | false | plugin_paymentCreate (wechat) |
| `p-pay-query-001` | false | classpath_sleep + plugin_paymentQuery |

### 导出时应识别的 plugin 依赖

从 BPMN `componentId` 扫描，**需打入 zip** 的 `plugin_*`：

| componentId | 提供 JAR |
|-------------|----------|
| `plugin_paymentCreate` | `kiwi-bpmn-component-payment-*-plugin.jar` |
| `plugin_paymentQuery` | `kiwi-bpmn-component-payment-*-plugin.jar` |
| `plugin_slackNotify` | `kiwi-bpmn-component-slack-*-plugin.jar` |

**不打入 zip**（`classpath_*`，随 Kiwi 核心发行）：

- `classpath_uuidGenerate`
- `classpath_assignmentActivity`
- `classpath_sleep`

`buildPluginJarIndex()` 已在 [`BpmComponentPluginLoader`](kiwi-admin/backend/src/main/java/com/kiwi/project/bpm/service/BpmComponentPluginLoader.java) 实现，任务 B 导出逻辑直接复用。

### manifest.requiredComponentKeys（预期超集）

导出扫描应至少包含：

```
plugin_paymentCreate
plugin_paymentQuery
plugin_slackNotify
classpath_uuidGenerate
classpath_assignmentActivity
classpath_sleep
```

## P0 本地验证（任务 B 前）

1. 空库启动（`local,dev`）→ Mongo migration 后出现「通用支付集成套件」
2. `GET /bpm/component/list` 含 `plugin_paymentCreate`、`plugin_paymentQuery`
3. 设计器打开 `p-pay-main-001`，节点 componentId 为 `plugin_*` / `classpath_*`
4. 填写沙箱 `ALIPAY_*` 或 `WECHAT_*` env 后手动跑 `pay-main`（可选）

```bash
mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests
# 从 kiwi-admin/backend 启动 Application
```

## 任务 B E2E 验收（完成后）

参照 [模板包捆绑组件jar_a901ea96.plan.md](模板包捆绑组件jar_a901ea96.plan.md)：

1. `GET /bpm/project/payment-integration-demo/export-template-file`（admin）
2. zip 含 `components/component-bundle.json` + **payment + slack** 两类 plugin JAR（core 组件为 classpath，不入 zip）
3. `manifest.kiwiManifest.requiredComponentKeys` 含上述 plugin 与 classpath 键
4. 导入到**无** payment/slack 插件的实例 → preview 显示依赖 → 冲突策略安装 JAR → `installPack`
5. 流程可在设计器打开；填 env 后可跑通主流程

## 任务 B 实施入口

续用计划：[`.cursor/plans/模板包捆绑组件jar_a901ea96.plan.md`](模板包捆绑组件jar_a901ea96.plan.md)

优先 todo：

1. `zip-format` — 扩展 `BpmTemplatePackBundleService.buildZip`
2. `jar-resolve` — 导出时调用 `buildPluginJarIndex()` 收集 JAR（索引已实现）
3. `jar-install` — `BpmComponentBundleService.installJarsFromBundle`
4. `import-preview-api` + `frontend-import-wizard`

OpenSpec change：`openspec/changes/bpm-workflow-template-market/`
