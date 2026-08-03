## ADDED Requirements

### Requirement: 每插件 JAR 使用子 ApplicationContext 加载

系统 SHALL 为 `plugins/` 下每个 JAR 创建独立 `AnnotationConfigApplicationContext`：`setParent(宿主)`、`setClassLoader(插件 URLClassLoader)`，经 `refresh()` 完成插件内部 Spring DI。SHALL **不再**在主上下文对插件类执行 `createBean` 注册非桥接 Bean。

#### Scenario: 子上下文完成多 Bean 注入

- **WHEN** 插件 JAR 含 `PaymentCreateActivity`（构造器注入 `PaymentChannelRouter`）及 Router/Adapter `@Component`
- **THEN** 子上下文 `refresh()` 后 SHALL 能 `getBean("paymentCreate")` 且 Router 依赖已解析

#### Scenario: reload 关闭旧子上下文

- **WHEN** 调用 `POST /bpm/component/plugins/reload` 或 Loader `reload()`
- **THEN** 先前各插件的 `ApplicationContext.close()` 与 `URLClassLoader.close()` SHALL 已执行，旧桥接 Bean SHALL 从宿主移除

---

### Requirement: component-bundle.json 子上下文引导字段

`component-bundle.json` SHALL 支持可选字段：

- `contextClass`：全限定 `@Configuration` 类名（优先）
- `scanPackages`：字符串数组，用于 `@ComponentScan` 等价扫描

二者均缺省时 SHALL 从 JAR 内 `@ComponentDescription` delegate 类包名推导根包并扫描（兼容旧 JAR）。

#### Scenario: contextClass 引导

- **WHEN** bundle 含 `contextClass: "com.example.PluginConfiguration"` 且该类为有效 `@Configuration`
- **THEN** 子上下文 SHALL `register` 该类后 `refresh()`，不依赖包推导

#### Scenario: scanPackages 引导

- **WHEN** bundle 含 `scanPackages: ["com.example.plugin"]` 且无 `contextClass`
- **THEN** 子上下文 SHALL 扫描所列包

---

### Requirement: Delegate 桥接至宿主

子上下文 `refresh()` 后，系统 SHALL 仅对带 `@ComponentDescription` 的 `JavaDelegate` / `ExternalTaskHandler`（及 `ActivityBehavior` delegate）在宿主注册桥接 Bean（bean 名与 `@Component` 或类名约定一致）。Operaton `getBean(beanName)` SHALL 获得可执行的 delegate。

#### Scenario: 宿主可解析 paymentCreate

- **WHEN** payment 插件已加载
- **THEN** 宿主 `containsBean("paymentCreate")` 为 true，且 `getBean("paymentCreate")` 实现 `JavaDelegate`
- **AND** `ClasspathBpmComponentProvider` SHALL 不将桥接 Bean 计为 `classpath_*`（`isPluginRegisteredBean` 为 true）

#### Scenario: 插件内部 Bean 不注册宿主

- **WHEN** payment 插件已加载
- **THEN** 宿主 SHALL 不包含 `paymentChannelRouter` 等非 delegate 单例（仅桥接 delegate 可见）
