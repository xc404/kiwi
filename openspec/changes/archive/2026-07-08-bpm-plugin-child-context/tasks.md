# Tasks — bpm-plugin-child-context

## 1. 契约与模型

- [x] OpenSpec change：`proposal` / `design` / `specs` / `tasks`
- [x] `BpmComponentBundleManifest` 增加 `contextClass`、`scanPackages`

## 2. 子上下文与桥接

- [x] `BpmPluginContext` — 持有 ClassLoader、子上下文、jar 名、桥接 bean 名列表
- [x] `BpmPluginContextManager` — `load` / `closeAll`；引导 `contextClass` / `scanPackages` / 包推导
- [x] `PluginDelegateBridge` — `JavaDelegate` / `ExternalTaskHandler` 桥接
- [x] 重构 `BpmComponentPluginLoader` 委托 `BpmPluginContextManager`

## 3. Payment 插件

- [x] `PaymentPluginConfiguration`（`@Configuration` + `@ComponentScan`）
- [x] `component-bundle.json` 增加 `contextClass`（可选）

## 4. 测试与文档

- [x] `BpmPluginContextManagerTest`：加载 payment JAR、DI、reload 关闭子上下文
- [x] 更新 `docs/bpm-component.zh-CN.md` 子上下文契约

## 5. 验证

- [x] `mvn -pl kiwi-admin/backend test -Dtest=BpmPluginContextManagerTest`
- [x] `mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests` 后启动含 payment 插件
