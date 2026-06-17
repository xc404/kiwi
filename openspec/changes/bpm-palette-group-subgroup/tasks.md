# Tasks

## 1. 元数据与后端

- [ ] `@ComponentDescription` 增加 `subGroup()`；`BpmComponent` 增加 `subGroup`；`BpmComponentDeploymentSignature` 纳入 subGroup
- [ ] `GET /bpm/component/list` 按 paletteGroup（基本/内置/扩展）→ subGroup 嵌套返回；`source=classpath` → 内置、`source=plugin` → 扩展
- [ ] 内置 classpath 组件：原 `group` 迁为 `subGroup`；`fillComponentProperties` 子组件继承 subGroup

## 2. 前端 Palette

- [ ] `BasePaletteProvider` Tab 改名为「基本组件」，现有 panel 作为 subGroup
- [ ] `component-provider` 嵌套类型；`ComponentPalleteProvider` 拆成内置/扩展两个 Tab 数据源
- [ ] `pallete` 三 Tab + Collapse=subGroup；默认仅首组展开；搜索跨两级

## 3. Context Pad 与验收

- [ ] Context Pad 弹层分组展示「内置组件/扩展组件 · subGroup」
- [ ] 验证三 Tab、插件 JAR 进扩展组件、classpath 进内置组件；旧 Mongo 兼容
