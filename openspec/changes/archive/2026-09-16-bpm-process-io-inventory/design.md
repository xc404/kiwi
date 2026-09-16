## Context

`BpmProcessIoAnalysisService` 已能从 BPMN 抽出流程级启动输入（`${var}` 且无上游产出）和输出并集，供 `as-component` / 另存为组件使用。结果是扁平列表：字面量填好的参数会消失，必填但未写入 XML 的参数不会进入启动缺口，`componentId` 只读 `camunda:property`。启动弹窗是空白 JSON + localStorage；Designer Agent 只有三个工具，改参前看不到缺口。

分析只做**设计时配置**，不读流程实例 `VariableMap`。

## Goals / Non-Goals

**Goals:**

- 一份按组件拓扑序展开的 IO 清单（已填/未填、字面量/表达式、是否被上游产出覆盖、启动仍缺的变量）。
- REST、启动弹窗、`list_process_io` 共用 `analyzeInventory`。
- 扁平 `processInputs` / `processOutputs` 从清单派生，保持 `as-component` 可用。
- `componentId` 解析与 Designer 对齐：`kiwi:componentId` → `camunda:property` → 无前缀属性。

**Non-Goals:**

- 流程实例运行时变量。
- 用户任务表单字段、网关条件里的 `${}`。
- 用项目环境变量自动填启动缺口。
- 启动弹窗从 JSON 编辑器改成表单（后续独立 change）。

## Decisions

### 1. 单一实现 `analyzeInventory`

`analyzeComponentIoGaps` / `wrapProcessAsComponent` 只读 `startVariables` 与 `processOutputs`，不再单独走图。避免两套遍历漂移。

备选：保留旧扁平遍历再另写清单。否决：必填隐式 `${key}` 与 `kiwi:componentId` 必须两边同时修。

### 2. 拓扑序 + 反向可达上游

节点顺序：`sequenceFlow` Kahn 拓扑，只保留绑定了 `componentId` 的 `serviceTask` / `callActivity`。上游产出：沿反向邻接 BFS，收集前驱目录 `outputParameters.key` 与 XML 自定义 `outputParameter` 名。

备选：前端已有的「当前节点上游」补全逻辑搬到后端。否决：后端需要全图清单，且要与 `as-component` 同源。

### 3. 已填判定与隐式必填

- `filled`：对应 `camunda:inputParameter` 文本 trim 后非空。
- `valueKind`：`Empty` | `Literal` | `Expression`（文本含 `${[a-zA-Z0-9_]+}`）。
- 目录必填且 XML 缺失/空白：视为隐式引用 `${key}`（与调色板 `ComponentService.initElement` 一致），`filled=false`，进入 `startVariables`（除非该 key 已被上游产出）。
- 字面量不进启动缺口。
- 输出没有运行时值：目录输出标为已产出；`processVariable` 优先目录 `defaultValue`，否则 `key`。匹配上游时用产出 **key**（及 XML 自定义输出名），与现有 gap 分析一致。

### 4. REST 两条路径

- `GET /bpm/process/{id}/io-inventory`（`bpmPd_getIoInventory`）：已保存 XML，鉴权同 `bpmPd_get`。
- `POST /bpm/process/io-inventory`（`bpmPd_analyzeIoInventory`）：body `{ bpmnXml }`，给画布未保存内容。启动弹窗必须走 POST。

返回单个对象，走统一 `R` 包装。`@Operation` 必填以便 MCP 扫描。

### 5. 启动弹窗仍用 JSON

打开前：导出画布 XML → `analyzeIoInventory` → `startVariables` 生成空骨架 → `BpmStartVariablesService.mergeSkeleton` 用 localStorage 覆盖 → `emptyKeys` 作为 `missingKeys` 提示。分析失败则回退仅缓存 / `{}`。

`firstValueFrom` 只出现在 toolbar 打开弹窗这一边界；服务层保持 Observable。

### 6. Agent 工具 `list_process_io`

分析 `turnContext` 当前 XML，返回按节点压缩文本（未填/未满足上游单独标出）。system prompt 改为四个工具。

## Risks / Trade-offs

- [未解析到 catalog 的 `componentId`] → 仍列出 XML 里出现的自定义 input/output，目录字段为空。
- [环或断开的 sequenceFlow] → Kahn 后剩余节点按 id 排序追加，不丢节点。
- [产出 key 与 `processVariable`/`defaultValue` 不一致] → 上游满足仍按 key 匹配，与历史 `processInputs` 语义一致；若日后要按变量名匹配需另开 change。
- [POST 分析任意 XML] → 与现有 `as-component` POST 一样只做静态解析，不执行流程。

## Migration Plan

纯增量：新 DTO / 新端点 / 新工具。旧 `as-component` 行为在隐式必填与 `kiwi:componentId` 上更完整，属修正而非破坏。无需数据迁移。回滚即去掉新端点与工具，恢复旧扁平遍历（不建议）。

## Open Questions

无。启动弹窗改表单已明确延后，不阻塞本 change 归档。
