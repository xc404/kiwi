## Why

BPM 设计器左侧组件面板当前将 `@ComponentDescription.group()` 同时当作 Tab 与功能分类，与「BPMN 原生 / classpath 内置 / plugin 扩展」三层来源混在一起，插件与内置组件无法清晰分区，用户难以区分系统能力与第三方扩展。

## What Changes

- 引入 **group / subGroup 二级分组**：顶层 `group` 固定为三个 Tab（基本组件、内置组件、扩展组件）；`subGroup` 为 Tab 内 Collapse 功能子类（承接原 `group` 语义）。
- `@ComponentDescription` 新增 `subGroup()`；`BpmComponent` 增加 `subGroup`；list API 返回按 Tab → subGroup 嵌套结构。
- 内置 classpath 组件将原 `group` 迁移为 `subGroup`；palette group 由 `source` 推导（`classpath` → 内置，`plugin` → 扩展）。
- 前端 Palette 三 Tab + Collapse；Context Pad 追加组件弹层按「paletteGroup · subGroup」分组。
- `BasePaletteProvider` Tab 改名为「基本组件」。

## Capabilities

### New Capabilities

- `bpm-palette-group-subgroup`：设计器组件面板二级分组、list API 嵌套响应、注解 subGroup 与 source 推导规则。

### Modified Capabilities

- （无：尚无对应 main spec。）

## Impact

- **注解**：`kiwi-bpmn-core` `ComponentDescription.java`
- **内置组件**：`kiwi-bpmn-component*` 全量 `group` → `subGroup` 迁移
- **后端**：`BpmComponent`、`BpmComponentService`、`BpmComponentCtl` list API、`BpmComponentDeploymentSignature`
- **前端**：`component-provider.ts`、`component-pallete-provider.ts`、`base-pallete-provider.ts`、`pallete.ts/html/scss`、context-pad 模块
