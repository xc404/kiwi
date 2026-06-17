## 任务

### A. 文档与契约（本目录）

- [x] `proposal.md`：摘要、范围、契约修正（Task 级流程 id、`Task`/`TaskDataset`/Movie、429 重试）
- [x] `design.md`、`tasks.md`：与 proposal 对齐（本轮同步）

### B. kiwi-admin

- [x] 流程变量启动；`POST /bpm/integration/process/{id}/start`（共享密钥）；`BpmMachineIntegrationCtl`
- ~~`GET /bpm/integration/process/{bpmProcessId}/capacity`~~（已移除；限额仅以 **`POST .../start` → 429** 表达）
- [x] **`POST .../start`**：达 `maxProcessInstances` 时返回 **429**（及稳定错误体），与 cryoEMS 重试策略配套
- [x] **`GET /bpm/integration/process-instances/{instanceId}/state`**（若已实现则保持）

### C. cryoEMS 模型与 Movie 门面

- [x] **`Task`**：新增 Kiwi `BpmProcess` 主键字段（参见 `design.md` 命名建议）
- [x] **`MovieKiwiWorkflowService`**：`ensureStarted`（或等价）签名/实现体现 **`Movie` + `Task` + `TaskDataset`**；从 **Task** 读取流程 id（禁止仅依赖全局 `movie-process-definition-id`，迁移期回退策略可选并在代码中标注下线条件）
- [x] **`MovieEngine`**：在调用门面时传入 **Task**、**TaskDataset**（按需从上下文加载）

### D. cryoEMS KiwiWorkflowClient

- [x] **移除**本地 `Semaphore` / `maxConcurrentRequests` 式并发闸门（若仍存在）
- ~~capacity HTTP / 缓存~~（已移除）
- [x] **`startProcess`**：**先请求**；遇 **429**（及契约内限流语义）按配置 **间隔重试**，不超过 **`maxStartAttempts`**
- [x] **`KiwiWorkflowProperties`**：补充/调整 **client** 项（重试间隔、最大次数等）；全局 **`movie-process-definition-id`** 改为可选迁移回退

### E. 运维与样本

- [x] **`application.yml` / 注释**：反映 Task 级配置为主、全局流程 id 为可选回退
- [x] 占位 BPMN `assets/cryo-movie-minimal.bpmn`（及 kiwi-admin samples 副本若已有）

### F. 明确延后（不要求本 change）

- [ ] kiwi-admin：在 BPMN 中 **实现** 真实 movie 处理步骤（业务后续迭代）
- [ ] cryoEMS：更完整的流程状态监听、与 Camunda 历史对齐（可另起 change）

### G. 已移除（错误实现）

- ~~`CryoemsMoviePipelineDelegate`（Kiwi → HTTP → cryoEMS）~~
- ~~cryoEMS `POST /internal/workflow/step` 及关联 Service~~
