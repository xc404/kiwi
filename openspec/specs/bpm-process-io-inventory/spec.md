# bpm-process-io-inventory Specification

## Purpose

设计时按组件拓扑列出流程 IO（已填/未填、字面量/表达式、是否被上游产出、启动缺口），同一分析供给 REST、启动流程弹窗与 Designer Agent 工具。不含流程实例运行时变量。

## Requirements

### Requirement: 按组件拓扑列出设计时 IO 清单

系统 SHALL 提供 `BpmProcessIoAnalysisService.analyzeInventory(bpmnXml)`，对绑定了组件的 `serviceTask` 与 `callActivity` 按 `sequenceFlow` 拓扑序返回 `BpmProcessIoInventory`。清单 SHALL 包含 `nodes`（每节点 `inputs`/`outputs`）、`startVariables` 与流程级 `processOutputs`。分析 SHALL 只读 BPMN 与组件目录，SHALL NOT 读取流程实例运行时变量。

`componentId` 解析顺序 SHALL 为：`kiwi:componentId` 扩展属性，然后 `camunda:property name="componentId"`，然后无前缀 `componentId` 属性。

#### Scenario: 节点顺序跟随连线

- **WHEN** 两个绑定组件的 serviceTask 由 sequenceFlow 串成 Start → A → B → End
- **THEN** `nodes` 的 `nodeId` 顺序 SHALL 为 A 然后 B

#### Scenario: 识别 kiwi:componentId

- **WHEN** 任务仅有 `kiwi:componentId`、没有 `camunda:property name="componentId"`
- **THEN** 该任务 SHALL 出现在 `nodes` 中，且 `componentId` 等于该扩展属性值

### Requirement: 输入已填判定与上游满足

对每个目录 `inputParameters` 项（跳过 `hidden`）以及 XML 中多出的 `camunda:inputParameter`，系统 SHALL 给出：`filled`（文本 trim 后非空）、`valueKind`（`Empty` / `Literal` / `Expression`）、`expressionRefs`（匹配 `${[a-zA-Z0-9_]+}`）、`satisfiedByUpstream`（全部引用都出现在反向可达前驱的产出 key 中）。

目录必填且 XML 缺失或空白时，系统 SHALL 将该输入视为隐式引用 `${key}`，`filled` 为 false，`valueKind` 为 `Empty`。

XML 中的字面量 SHALL 标为 `Literal`，且 SHALL NOT 仅因该参数已填而从清单中省略。

#### Scenario: 上游产出满足表达式输入

- **WHEN** 上游组件目录声明产出 key `uuid`，下游输入配置为 `${uuid}`
- **THEN** 该输入 `valueKind` SHALL 为 `Expression`，`satisfiedByUpstream` SHALL 为 true

#### Scenario: 字面量保留在节点输入中

- **WHEN** HTTP 组件输入 `url` 配置为 `https://example.test`
- **THEN** 该输入 SHALL 出现在对应节点的 `inputs` 中，`valueKind` SHALL 为 `Literal`，`filled` SHALL 为 true

#### Scenario: 必填参数未写入 XML

- **WHEN** 目录声明 `url` 为必填，且任务 XML 没有对应 `inputParameter`
- **THEN** 该输入 `filled` SHALL 为 false，`expressionRefs` SHALL 包含 `url`

### Requirement: 启动缺口 startVariables

`startVariables` SHALL 收集被输入引用、且未被任何反向可达前驱产出的流程变量。字面量输入 SHALL NOT 产生启动缺口。同一 key 只保留首次出现（拓扑序）。每条 SHALL 带上首次出现的 `nodeId` 与 `parameterKey`。

`analyzeComponentIoGaps` 与 `wrapProcessAsComponent` 的流程级输入 SHALL 从 `startVariables` 派生，流程级输出 SHALL 从 `processOutputs` 派生。

#### Scenario: 无上游的表达式成为启动变量

- **WHEN** HTTP 输入为 `${requestUrl}` 且图中无前驱产出 `requestUrl`
- **THEN** `startVariables` SHALL 包含 key `requestUrl`，`nodeId` 为该 HTTP 任务，`parameterKey` 为 `url`

#### Scenario: 字面量不是启动缺口

- **WHEN** HTTP 输入 `url` 为字面量 URL
- **THEN** `startVariables` SHALL NOT 包含因该输入而产生的条目

#### Scenario: 上游已覆盖则无启动缺口

- **WHEN** 下游输入 `${uuid}` 且上游产出 `uuid`
- **THEN** `startVariables` SHALL 为空（就该引用而言）

#### Scenario: 隐式必填进入启动缺口

- **WHEN** 必填 `url` 完全没有 `inputParameter`，且无上游产出 `url`
- **THEN** `startVariables` SHALL 包含 key `url`

### Requirement: REST 查询已保存图与画布 XML

系统 SHALL 提供：

- `GET /bpm/process/{id}/io-inventory`，`operationId` 为 `bpmPd_getIoInventory`，对调用方拥有的已保存流程 XML 返回清单。
- `POST /bpm/process/io-inventory`，`operationId` 为 `bpmPd_analyzeIoInventory`，请求体含 `bpmnXml`，返回该 XML 的清单。

两端点 SHALL 带 `@Operation`。空 XML SHALL 拒绝。GET SHALL 使用与读取该流程定义相同的所有权校验。

#### Scenario: 按流程 id 取清单

- **WHEN** 已登录用户请求自己拥有的流程的 `GET .../io-inventory`
- **THEN** 响应 SHALL 为该流程已保存 BPMN 的 `BpmProcessIoInventory`

#### Scenario: 分析未保存画布

- **WHEN** 客户端 POST `{ "bpmnXml": "<valid bpmn>" }` 到 `/bpm/process/io-inventory`
- **THEN** 响应 SHALL 为对该字符串分析得到的清单，SHALL NOT 要求先保存流程定义

### Requirement: 启动弹窗用清单预填 JSON

打开启动流程弹窗前，设计器 SHALL 导出当前画布 XML，调用 `POST /bpm/process/io-inventory`，用返回的 `startVariables` 生成 JSON 对象骨架（缺省空字符串），再与该流程在 localStorage 中缓存的启动变量合并（缓存覆盖空骨架，可保留额外 key）。弹窗 SHALL 仍使用 JSON 编辑器，并 MAY 提示合并后仍为空的启动 key。分析失败时 SHALL 回退为仅使用缓存或 `{}`。

#### Scenario: 骨架与缓存合并

- **WHEN** 清单给出启动 key `requestUrl`，localStorage 已有 `{ "requestUrl": "https://a.test" }`
- **THEN** 编辑器初始 JSON SHALL 含 `"requestUrl": "https://a.test"`

#### Scenario: 分析失败仍可启动

- **WHEN** IO 清单请求失败
- **THEN** 弹窗 SHALL 仍打开，初始文本为缓存对象或 `{}`

### Requirement: Designer Agent list_process_io

Designer Agent 工具集 SHALL 包含 `list_process_io`。该工具 SHALL 分析当前回合工作区 BPMN，返回按节点压缩的文本，标出已填/未填以及启动仍缺的变量。工作区 XML 为空时 SHALL 返回说明性错误而不抛给模型未处理异常。

#### Scenario: 改参前可列出缺口

- **WHEN** 工作区含绑定组件的图，且某必填输入未填
- **THEN** `list_process_io` 的返回文本 SHALL 包含该节点 id 或名称，并标出该未填输入
