# Tasks: bpm-component-bundle-metadata

## 1. 后端模型与读取

- [x] 1.1 `BpmComponentBundleManifest` + `BpmComponentBundleComponentEntry`
- [x] 1.2 `BpmComponentPluginDescriptor` + `BpmComponentPluginComponentInfo` DTO
- [x] 1.3 `BpmComponentBundleReader`（readFromJar、describeJar、scanJarComponents）
- [x] 1.4 `BpmComponentBundleReaderTest`

## 2. Loader / Service / API

- [x] 2.1 `BpmComponentPluginLoader.describeInstalledPlugins` + reload 缓存
- [x] 2.2 `BpmComponentBundleService` preview + upload 校验
- [x] 2.3 `BpmComponentCtl` 端点与 `@Operation`

## 3. 前端

- [x] 3.1 `bpm-component-plugin.types.ts`
- [x] 3.2 插件页表格列、展开、preview 确认弹窗

## 4. 构建与文档

- [x] 4.1 example + 官方 component 模块内嵌 bundle JSON
- [x] 4.2 `docs/bpm-component.zh-CN.md` 清单章节

## 5. 路线图

- [x] 5.1 `phase2-component-bundle-json` → completed
- [x] 5.2 `phase4-market-ui` 备注更新为含元数据预览
