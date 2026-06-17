## Why

BPM 设计器属性面板通过 `CompositePropertyProvider` 合并「通用基础」与「组件绑定」等来源的属性 Tab；当前实现将 `BasePropertyProvider` 与 `ComponentPropertyProvider` 在类内硬编码注入，新增业务域（如网关条件、监听器、自定义元素）若要贡献 Tab/分组，只能修改合成类或重复合并逻辑，扩展点不清晰，也不利于按功能模块拆分。

## What Changes

- 引入可注册的 **属性提供者贡献（contributors）** 机制：`CompositePropertyProvider` 不再写死两个具体类，而是合并一组实现了 `PropertyProvider` 的贡献者（顺序可约定或由注册顺序决定）。
- 通过 Angular DI **多提供者（`multi: true`）** 或等价注册 API，使各特性模块（或懒加载区域）能声明自己的 `PropertyProvider`，无需改动核心合成类。
- 保留现有默认行为：基础 Tab（`BasePropertyProvider`）与组件输入/输出 Tab（`ComponentPropertyProvider`）作为默认注册的贡献者，合并规则（同名 Tab 合并 groups）保持不变。
- **无 BREAKING**：对外 `PROPERTY_PROVIDER` token 及 `PropertyProvider` 接口保持；仅内部装配方式从固定两类改为可扩展列表。

## Capabilities

### New Capabilities

- `bpm-property-provider-registry`: 定义 BPM 属性面板如何通过可注册的 `PropertyProvider` 列表组装 Tab，以及默认贡献者与合并语义（同名 Tab 合并 groups）。

### Modified Capabilities

- （无）仓库 `openspec/specs/` 下尚无 BPM 前端相关基线规格；本次仅新增能力规格。

## Impact

- **前端**：`kiwi-admin/frontend` 下 `property-provider.ts`（及可能的 `app.config` / 路由特性模块中的 `provide*` 注册）。
- **模块边界**：`flow-elements` 中的 `ComponentPropertyProvider` 可作为默认贡献者之一注册，而非被 `CompositePropertyProvider` 直接 import。
- **扩展方**：其他目录下的新 `PropertyProvider` 实现可通过 DI 多提供者加入，无需修改属性面板组件 `BpmPropertiesPanel`。
