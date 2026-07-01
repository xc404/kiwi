# Change: 组件包 component-bundle.json 元数据清单

## Why

组件插件页此前仅展示 JAR 文件名，无法预览包名、版本与组件列表；第三方插件也缺少统一的包级说明契约。需要内嵌 `META-INF/kiwi/component-bundle.json`，并在上传前 preview 校验。

## What Changes

- 新增 `BpmComponentBundleManifest` 模型与 `BpmComponentBundleReader`（读 JAR、合并注解扫描、校验 warnings）
- **BREAKING**：`GET /bpm/component/plugins` 由 `List<String>` 改为 `List<BpmComponentPluginDescriptor>`
- 新增 `POST /bpm/component/plugins/preview`（multipart，不落盘）
- `upload` / `reload` / `delete` 同样返回 descriptor 列表
- 前端组件插件页：包信息列、行展开组件列表、上传 preview 确认
- 官方与 example 模块内嵌 `component-bundle.json`；文档增补清单章节

## Impact

- Affected specs: `bpm-component-plugins`
- Affected code: `BpmComponentPluginLoader`, `BpmComponentBundleService`, `BpmComponentCtl`, `bpm-component-plugins.ts`
- API 消费者需从 `content[].fileName` 迁移为读取 `BpmComponentPluginDescriptor`
