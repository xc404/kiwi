## 1. 后端清单

- [x] 1.1 新增 `BpmProcessIoInventory`（Node / Param / StartVariable，Direction / ValueKind）
- [x] 1.2 `analyzeInventory`：拓扑序、kiwi:componentId、隐式必填、字面量 vs 表达式、上游产出
- [x] 1.3 `analyzeComponentIoGaps` / `wrapProcessAsComponent` 从 inventory 派生
- [x] 1.4 `GET {id}/io-inventory`（`bpmPd_getIoInventory`）与 `POST io-inventory`（`bpmPd_analyzeIoInventory`）

## 2. 测试

- [x] 2.1 `BpmProcessIoAnalysisServiceTest`：上游满足、启动缺口、字面量、隐式必填、kiwi:componentId、节点顺序
- [x] 2.2 `DesignerHarnessToolsTest`：`list_process_io` 文本断言

## 3. Designer Agent 与前端

- [x] 3.1 工具 `list_process_io` + system prompt 四个工具
- [x] 3.2 前端类型与 `getIoInventory` / `analyzeIoInventory`
- [x] 3.3 启动弹窗：骨架与 localStorage 合并、`missingKeys` 提示；仍用 JSON 编辑器
