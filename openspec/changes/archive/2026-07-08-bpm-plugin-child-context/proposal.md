# Change: BPM 插件子 ApplicationContext

## Why

当前 `BpmComponentPluginLoader` 在主上下文用 `createBean` 仅注册 `JavaDelegate` 入口类，无法完成插件内部多 Bean 依赖注入（如 Payment 的 `PaymentChannelRouter` → `AlipayChannelAdapter`）。Payment 等按 classpath Spring 模型编写的插件在 plugin 模型下必然失败。需为每个插件 JAR 建立独立子 `ApplicationContext`，并将 delegate 桥接到宿主供 Operaton 解析。

## What Changes

- `component-bundle.json` 扩展可选字段 `contextClass`、`scanPackages`（`contextClass` 优先；均无则从 delegate 包名推导根包扫描）
- 新增 `BpmPluginContext`、`BpmPluginContextManager`、`PluginDelegateBridge`：每 JAR 子上下文 `refresh`，仅桥接 `JavaDelegate` / `ExternalTaskHandler` 至宿主
- 重构 `BpmComponentPluginLoader`：卸载时 `childCtx.close()` + 销毁桥接 Bean，替代主上下文 `createBean` / `autowireBean`
- Payment 模块新增 `PaymentPluginConfiguration`（`@Configuration` + `@ComponentScan`）
- 补充插件 reload 集成测试（子上下文关闭、Bean 替换、Payment delegate 可解析）

## Capabilities

### New Capabilities

（无独立新 capability；行为归入既有 `bpm-component-plugins`）

### Modified Capabilities

- `bpm-component-plugins`: 插件加载改为子 ApplicationContext + 宿主桥接；清单契约增加 `contextClass` / `scanPackages`；reload 生命周期关闭子上下文

## Impact

- Affected specs: `bpm-component-plugins`
- Affected code: `BpmComponentPluginLoader`, `BpmPluginContextManager`, `BpmComponentBundleManifest`, `kiwi-bpmn-component-payment`
- 插件作者可在子上下文内使用标准 Spring DI；禁止依赖 Boot `@ConditionalOnBean`（子上下文非 Boot 应用）
- 宿主 SPI（`JdbcConnectionSupplier` 等）经 parent 上下文注入
