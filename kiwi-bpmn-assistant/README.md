# kiwi-bpmn-assistant

开源风格的 BPMN 助手库：LLM 产出 **JSON Plan IR**，由确定性编译器生成可导入的 BPMN XML（思路对齐 [jtlicardo/bpmn-assistant](https://github.com/jtlicardo/bpmn-assistant)）。

## 架构要点

| 层 | 职责 |
|----|------|
| **Plan IR** | 窄 JSON 中间表示：`processId` / `nodes` / `flows`，不含原始 BPMN XML 字符串 |
| **Compiler** | `AssistantPlanCompiler` 将 IR 编译为带 `xmlns:kiwi="http://kiwi.com/bpmn"` 的 BPMN 2.0 XML |
| **BPMN→Plan** | `AssistantBpmnToPlan` 在 modify 模式下把上一版 XML 解析回 IR，供 prompt 与增量修改 |
| **Rules** | `assistant/plan-ir-rules.json`：软规则进 LLM prompt；硬规则由校验器引用（面向 Plan IR，非手写 XML） |
| **SPI** | `AssistantComponentLookup` / `AssistantBpmnLookup` / `AssistantXmlValidator` 由宿主（如 kiwi-admin）实现 |
| **Session** | `WriteWorkflowSession` / `WriteWorkflowStatus`：写工作流会话状态（无 Operaton 元流程） |

**约定：永远不直接采用 LLM 输出的 `candidateXml`；只编译 `planIrJson`。**

## 配置

```yaml
kiwi:
  ai:
    write-workflow:
      enabled: false
      max-repair-rounds: 3
      catalog-installed-top-n: 40
      catalog-template-top-n: 8
      catalog-installable-top-n: 15
```

## 包结构

- `com.kiwi.bpmn.assistant` — 核心服务、IR、规则、校验、会话模型
- `com.kiwi.bpmn.assistant.spi` — 宿主扩展点

## 留在 kiwi-admin 的内容

写工作流编排属于应用层：

- `WriteWorkflowOrchestrator` / `WriteWorkflowSessionService` / `AssistantIntentService`
- `WriteWorkflowCtl`（`/ai/write-workflow/**`）
- Catalog 组装与 SPI 适配器
- （遗留）可选 `JavaDelegate` 壳，已不再部署元流程 BPMN
