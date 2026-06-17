## Context

`CompositePropertyProvider`（`property-provider.ts`）当前通过 `inject(BasePropertyProvider)` 与 `inject(ComponentPropertyProvider)` 固定合并两处属性来源，合并算法（按 Tab `name` 合并 `groups`）已可用。扩展需求是：其他模块在不修改该合成类的前提下，增加更多 `PropertyProvider` 实现。

约束：Angular 20+ standalone / `providedIn: 'root'` 风格；属性面板通过 `inject(PROPERTY_PROVIDER)` 消费单一合成入口。

## Goals / Non-Goals

**Goals:**

- 用 Angular DI 暴露「多个 `PropertyProvider` 贡献者」的注册能力，合成类仅依赖贡献者列表与既有合并逻辑。
- 默认行为与当前一致：最终 Tab 集合仍为「基础信息」+（针对 ServiceTask/CallActivity 等的）「输入」「输出」等。
- 合并顺序可文档化（例如：基础最先，其后按注册顺序叠加），便于预测同名 Tab 的合并结果。

**Non-Goals:**

- 不要求插件热插拔或运行时从远端加载脚本；仍限于编译期打包的 Angular 模块。
- 不改变 `PropertyProvider.getProperties(Element)` 接口签名。
- 不在此变更中实现具体新业务 Tab（仅建立扩展机制）。

## Decisions

### D1：使用 `InjectionToken` + `multi: true` 注册贡献者

- **做法**：定义例如 `PROPERTY_PROVIDER_CONTRIBUTORS = new InjectionToken<PropertyProvider[]>(..., { factory: () => [], providedIn: 'root' })` 的模式需注意：`multi` 通常在 providers 数组中配置；更常见写法是 `new InjectionToken('...')` 配合 `providers: [{ provide: TOKEN, useClass: X, multi: true }]`，或在 `provide*` 辅助函数里封装。
- **备选**：手写单例 `PropertyProviderRegistry` + `register()` — 更灵活但易与 DI 生命周期不一致，测试需额外重置。
- **结论**：优先 **multi provider**，与 Angular 惯例一致，`CompositePropertyProvider` 内 `inject<PropertyProvider[]>(PROPERTY_PROVIDER_CONTRIBUTORS)`（若 token 类型为数组需注意 Angular 对 multi 注入返回数组）。

（实现时注意：Angular 中 multi token 注入得到的是 **所有 multi 提供者实例的数组**，需在应用 bootstrap 层或对应 `ApplicationConfig`/`Route` 的 `providers` 中为每个贡献者追加 `{ provide: CONTRIBUTORS, useExisting: BasePropertyProvider, multi: true }` 等，避免重复实例：可用 `useExisting` 指向已有的 root 单例。）

### D2：默认贡献者的装配位置

- **做法**：在 BPM 设计相关入口（如加载设计器的模块 `providers` 或 `app.config.ts` 中与设计器同区的 `provideEnvironmentProviders`）注册三条 multi：`BasePropertyProvider`、`ComponentPropertyProvider`，顺序反映合并优先级。
- **结论**：核心合成类不再直接 `inject` 这两个类；它们仅作为默认贡献者出现在 providers 列表中。

### D3：合并顺序与同名 Tab

- **做法**：保持现有算法：依次对每个贡献者的 `PropertyTab[]` 做归并；同名 Tab 的 `groups` 拼接。
- **结论**：文档与规格中写明「按贡献者注册顺序依次合并」，新增贡献者通过把自己排在默认链之前或之后控制优先级。

## Risks / Trade-offs

- **[Risk] Multi provider 顺序依赖配置** → **Mitigation**：在规格中写明顺序语义；必要时提供小型 `provideBpmDefaultPropertyProviders()` 封装固定顺序。
- **[Risk] `useClass` 导致重复实例** → **Mitigation**：默认贡献者用 `useExisting` 绑定已 `providedIn: 'root'` 的服务。
- **[Trade-off]** 纯 DI 扩展无法做到「零 import」的业务模块懒加载贡献——若贡献者在懒加载路由中注册 multi，需确保该路由在进入设计器前已加载（或由设计器路由统一 import）。

## Migration Plan

1. 引入 contributor token 与改造 `CompositePropertyProvider`。
2. 在现有应用 providers 中注册默认两条 multi。
3. 验证属性面板元素切换与 ServiceTask/CallActivity Tab 与现网一致。
4. 若有集成测试或 e2e，覆盖选中元素后 Tab 数量与关键字段仍存在。

回滚：恢复合成类内双注入并移除 multi 注册即可。

## Open Questions

- 是否在后续迭代提供 `providePropertyProviderContributor(p: Type<PropertyProvider>)` 助手以减少样板代码（本次可选）。
