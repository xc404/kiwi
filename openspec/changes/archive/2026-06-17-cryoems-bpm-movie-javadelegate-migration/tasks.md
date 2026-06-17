## 1. 流程输入契约与迁移边界梳理

- [x] 1.1 盘点 movie 现有 handler 步骤，标记首批迁移到 `JavaDelegate` 的步骤键与 BPM 节点映射
- [x] 1.2 在 `MovieKiwiWorkflowService` 相关路径补充输入契约说明，确认 `movie`、`task`、`taskDataset`（可选）字段与缺省行为
- [x] 1.3 明确迁移边界与回退策略（流程版本或任务配置开关），形成可实施约定
- [x] 1.4 明确执行职责归属：`kiwi-admin` 为流程最终执行入口，`cyroems-bpm` 提供流程能力模块

## 2. cryoems-bpm Delegate 基础设施

- [x] 2.1 在 `cryoems-bpm` 新增 movie delegate 基础抽象（变量读取、参数校验、日志上下文）
- [x] 2.2 定义“平铺输出变量字典”，覆盖成功状态、错误码、错误信息、可重试语义、fatal 标识及业务数据字段
- [x] 2.3 实现 StepResult 语义到平铺流程变量的统一映射，并明确禁止输出 `StepResult` 对象
- [x] 2.4 为平铺变量映射编写单元测试，覆盖成功、业务异常、系统异常三类场景
- [x] 2.5 将多 delegate 的公共操作下沉到父类或 service（如上下文读取、异常包装、日志与结果变量写入）
- [x] 2.6 为公共父类/service 增加复用性测试，验证不同 delegate 使用时行为一致

## 3. kiwi-admin 依赖装配与流程接入

- [x] 3.1 在 `kiwi-admin` 引入 `cyroems-bpm` 依赖并验证依赖树无冲突
- [x] 3.2 在 `kiwi-admin` 完成 movie 流程与 delegate 的运行时装配验证（含 Bean/流程定义可发现性）

## 4. 首批步骤迁移与流程接入

- [x] 4.1 选择一个核心步骤，将原 handler 逻辑迁移为具体 `JavaDelegate` 实现
- [x] 4.2 调整对应 BPMN 流程节点绑定到新 delegate，并保留未迁移步骤兼容路径
- [x] 4.3 增加集成测试/联调用例，验证从 `MovieKiwiWorkflowService` 启动到 `kiwi-admin` 节点执行的端到端链路
- [x] 4.4 在首批迁移步骤中验证“具体 delegate 仅保留业务特有逻辑”，公共逻辑全部走父类/service
- [x] 4.5 在首批迁移步骤中验证流程分支仅依赖平铺输出变量，不依赖 `StepResult` 对象

## 5. 灰度发布与回退验证

- [ ] 5.1 在测试或预发布环境开启迁移步骤，验证日志、状态变量、失败分支行为
- [x] 5.2 演练回退开关，确认异常情况下可快速切回兼容执行路径
- [x] 5.3 输出迁移完成标准（DoD）与后续步骤扩展计划，准备下一批 handler 迁移
