## Why

BPM 设计页（`bpm-editor`）功能集中在工具栏与画布操作，用户需熟悉 bpmn-js 快捷键与组件面板。希望在同一页面通过 **AI 对话**完成：与工具栏等价的操作、对当前流程 BPMN 的修改意图落地，以及借助后端对**组件目录与语义**的理解，将「添加某类业务组件」安全落到画布上，降低学习成本与误配风险。

## What Changes

- **前端**：在 BPM 设计器页面嵌入 AI 对话（复用 `app-chat` / `ChatComponent` 模式或同等交互），与 `BpmEditorToken` / `BpmnModeler` 桥接：解析后端返回的**结构化动作**并执行（工具栏指令、导入/合并 BPMN XML、按组件元数据追加节点等）。
- **后端**：新增或扩展 **BPM 设计器专用** AI 接口（可与现有 `/ai/assistant` 的 Spring AI + Tool 模式对齐）：在 system 上下文中注入当前流程 id、BPMN 摘要或片段、可用组件列表（来自现有组件服务/目录）；通过 **ToolCallback**（或等价机制）登记「执行类」操作，将模型输出转为前端可执行 `actions` + 自然语言 `content`。
- **组件添加**：后端工具可解析用户意图 → 匹配 `ComponentDescription`（名称/标签/能力）→ 返回明确的前端动作（例如 `appendComponent`：组件 id + 建议锚点），前端复用 `appendComponentFromContextPad` / `ComponentService` 路径落图，必要时再触发保存。

## Capabilities

### New Capabilities

- `bpm-editor-ai-assistant`：BPM 设计器内 AI 对话、工具栏级动作代理、BPMN 修改与组件智能追加的契约与行为。

### Modified Capabilities

- 可能扩展全局 `AiAssistantResponse` 的动作类型枚举，或新增专用响应 DTO（实现阶段二选一，避免破坏现有 `/ai/assistant` 消费者）。

## Impact

- **前端**：`bpm-editor` 布局与模块、`BpmToolbar` 可复用的能力清单、新建轻量 `BpmEditorAiBridge`（或等价）负责执行动作队列与错误提示。
- **后端**：`com.kiwi.project.ai` 或 `bpm` 包下新增 Assistant 服务、Tool 定义、REST；可选依赖现有组件查询 API。
- **非目标（首版可不做）**：流式 SSE、多模态、全自动无确认覆盖整图 XML、未登录场景。

## Non-goals

- 替换现有工具栏为纯 AI 操作；AI 为辅助通道。
- 在未启用 `kiwi.ai` 时仍须零依赖降级（隐藏入口或提示不可用）。
