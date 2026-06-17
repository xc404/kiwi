## 1. Processor API consolidation

- [x] 1.1 在 `SlurmJobCompleteProcessor` 中收敛为单一对外 `complete(...)` 入口，并将 parsed sacct 适配流程改为内部 helper。
- [x] 1.2 将 `processTerminal` / `processParsedSlurmTerminal` 及相关 `process*` helper 统一重命名为 `complete*` 语义命名（区分对外入口与内部方法）。
- [x] 1.3 校验并保持终态处理行为不变：幂等判断、Mongo 乐观锁、Camunda complete/handleFailure、重试语义。

## 2. Call-site and test alignment

- [x] 2.1 更新 `SlurmJobTracker` 对处理器的调用，全部切换到新的单一 `complete(...)` 对外入口。
- [x] 2.2 更新 `SlurmJobTrackerTest` 中 mock/verify 与断言，匹配新方法签名与命名。
- [x] 2.3 全局检索旧签名调用，确认不存在遗留对已废弃入口或旧命名的引用。

## 3. Verification

- [x] 3.1 对本次变更涉及文件执行静态检查（lints）并修复新增问题。
- [x] 3.2 在可用依赖环境下执行 `kiwi-bpmn-component` 编译/测试验证，确认重构未引入功能回归。
