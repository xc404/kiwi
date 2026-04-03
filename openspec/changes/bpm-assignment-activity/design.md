## Context

Kiwi BPM 中可复用单元以 `@ComponentDescription` 标注的 **Activity**（`AbstractBpmnActivityBehavior`）形式存在；Spring Bean 名作为 `BpmComponent.key`，供服务任务委托解析。赋值需求应复用该机制，避免引入新 BPMN 元素类型。

## Goals / Non-goals

**Goals:**

- 支持在一次执行中批量 `setVariable`。
- 输入在设计器侧为**流程变量驱动的参数**（与其它组件一致：`assignments` 等 key 由组件元数据声明，运行时从 `DelegateExecution` 读值）。
- 对 `${var}` 形式提供**仅一层**变量解析（读取已有流程变量），满足常见「把 A 拷到 B」场景。

**Non-goals:**

- 不实现完整 Camunda/JUEL 表达式引擎（项目中未统一使用 `ExpressionManager`）。
- 不要求在属性面板增加专用控件；`assignments` 使用 `#text` 多行文本即可。
- 不在此变更中修改 `kiwi-admin` 前端组件选择逻辑（除非发现硬编码白名单——当前未发现则不动）。

## Decisions

1. **类与 Bean 名**：`com.kiwi.bpmn.component.activity.AssignmentActivity`，`@Component("assignmentActivity")`，`@ComponentDescription` 的 `name` 为「赋值」或「变量赋值」，`group` 建议 `流程控制` 或 `通用`（与产品文案一致即可）。
2. **输入契约**：单一必填逻辑参数 `assignments`（`htmlType`: `#text`）：值为 **JSON 对象**字符串，例如 `{"targetA": 1, "targetB": "${sourceX}"}`。
   - 解析：与 `MongoActivity` 类似，使用 **`org.bson.Document.parse`** 将顶层解析为键值对（项目内已有用法，避免新增依赖）。
   - 对每个 entry：若值为字符串且整体匹配 `\$\{([a-zA-Z0-9_]+)\}`，则 `execution.getVariable(组1)` 的结果写入目标 key；否则按 JSON 解析得到的 Java 类型直接 `setVariable`。
3. **无输出参数**：赋值不产生命名输出；`outputs` 为空数组即可（与「仅副作用」一致）。
4. **错误语义**：`assignments` 缺失、非对象 JSON、或 `${name}` 引用不存在的变量时，抛出明确 `IllegalArgumentException`（或项目内统一异常），便于实例排查。

## Risks / Trade-offs

- **[Trade-off]** `${}` 仅支持简单变量名，不支持属性路径（如 `customer.id`）→ 可先文档化；后续可扩展。
- **[Risk]** 大 JSON 字符串在设计器中单字段编辑体验一般 → 接受；与 Mongo 组件同类。

## Migration Plan

- 无数据迁移；新组件随应用启动与自动部署进入库。
- 回滚：删除该类或取消部署即可，不影响已部署流程 XML（已保存的 `componentId` 会失效，需人工改图）。

## Open Questions

- 无。
