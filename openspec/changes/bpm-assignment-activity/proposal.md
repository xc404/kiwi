## Why

流程中经常需要在**不调用外部服务**的情况下，把常量或其它流程变量的值写入一个或多个流程变量（赋值）。当前组件库已有 Mongo、Shell、测试组件等，但缺少一个轻量、只操作流程变量上下文的「赋值」类 Activity，设计者需要在服务任务上挂接专用 Spring Bean 才能完成同类能力。

## What Changes

- 在 `kiwi-bpmn-component` 中新增 **`AssignmentActivity`**（Spring Bean，继承 `AbstractBpmnActivityBehavior`），通过 `@ComponentDescription` 注册为 BPM **组件**，由现有 `ClasspathBpmComponentProvider` 扫描并（在开启自动部署时）同步到设计器可选项。
- 通过**单一输入参数**（JSON 字符串）描述多组「目标变量名 → 值」；值支持 JSON 字面量，并对**纯字符串**形式 `${sourceVar}` 约定为从流程变量 `sourceVar` **读取后赋给目标键**（非完整 EL，仅简单变量引用）。
- 不修改 BPMN 图元类型：仍使用 **`bpmn:ServiceTask` + `componentId` 指向该组件**（与现有组件一致）。

## Capabilities

### New Capabilities

- `bpm-assignment-activity`：定义「赋值」组件在运行时的变量写入语义与输入格式。

### Modified Capabilities

- （无。）

## Impact

- **运行时**：`kiwi-bpmn-component` 新增一个类；无前端 `kiwi.json` 扩展元数据变更（组件列表来自后端）。
- **设计器**：部署/刷新组件后，调色板或组件选择器中可出现新组件（行为与现有 SpringBean 组件一致）。
- **配置**：无新增必选配置项。
