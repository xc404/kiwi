## 1. 接口与异常

- [x] 1.1 在 `IRetry` 新增 `default boolean decreaseRetries() { return true; }`，并补 JavaDoc 说明用途
- [x] 1.2 在 `JobRetryFailureSupport` 新增 `findIRetryOnChain(Throwable)`，与 `isIRetryOnChain` 对称
- [x] 1.3 新增 `SlurmOverloadedException`（`com.kiwi.bpmn.component.slurm` 包，`RuntimeException implements IRetry`，覆盖 `decreaseRetries()` 返回 `false`）

## 2. 配置与仓储

- [x] 2.1 在 `SlurmProperties` 新增 `maxConcurrentJobs`（默认 `50`），并补 JavaDoc 说明 `<= 0` 为不限流
- [x] 2.2 在 `ExternalTaskRetryProperties` 新增 `nonDecreasingRetryCycle`（默认 `R5/PT30S`）；不引入 FQCN 字符串匹配
- [x] 2.3 在 `SlurmJobRepository` 新增 `long countByStatus(SlurmJobStatus status)`（派生方法，无需自定义 `@Query`）

## 3. 提交前闸门

- [x] 3.1 在 `SlurmExternalTaskHandler` 注入 `SlurmProperties` 与 `SlurmJobRepository`（均可为 `@Autowired(required=false)`；旧路径不依赖时不影响 Shell 回退）
- [x] 3.2 在 `executeAsync` 进入 Slurm 提交分支后、构造 `SbatchConfig` 之前，按 `maxConcurrentJobs` 阈值做计数判断
- [x] 3.3 超阈值时输出 `WARN Slurm overloaded` 日志（字段：runningCount、maxConcurrentJobs、processInstanceId、activityId、externalTaskId、businessKey），并抛出 `SlurmOverloadedException("Slurm overloaded: running=N, max=M")`
- [x] 3.4 限流关闭（`maxConcurrentJobs <= 0`）时短路跳过计数查询

## 4. 重试规划

- [x] 4.1 修改 `ExternalTaskRetryPlanner` 构造器，新增可选参数 `nonDecreasingRetryCycle`；保留原 3 参构造器以保证向后兼容
- [x] 4.2 在 `plan(ExternalTask, Throwable)` 中，OLE 分支之后新增 "非递减重试" 分支：通过 `JobRetryFailureSupport.findIRetryOnChain(f)` 取 `IRetry` 实例；若 `!decreaseRetries()` 则按"保留 retries + 解析 nonDecreasingRetryCycle 得到 retryTimeoutMs"返回 `RetryPlan`
- [x] 4.3 退避周期解析失败 / 配置缺失时回退到 BPMN / 引擎默认 cycle 的第一个间隔；二者都失败时兜底 30000ms；首次失败用 cycle 的 retries 值作为初值
- [x] 4.4 更新 `ExternalTaskRetryAutoConfiguration`，把 `nonDecreasingRetryCycle` 注入到 `ExternalTaskRetryPlanner` Bean

## 5. 验证

- [x] 5.1 本地编译通过：`mvn -pl kiwi-bpmn/kiwi-bpmn-component,kiwi-bpmn/kiwi-bpmn-external-task -am clean compile`（包含 `kiwi-bpmn-core` 重编）
- [x] 5.2 `RetryPlannerTest` 5/5 通过（含 3 个新增用例：非递减保留 retries / 首次失败使用 cycle retries 初值 / 默认 IRetry 走标准分支）
- [x] 5.3 `openspec validate slurm-overload-flow-control --strict` 通过
- [ ] 5.4 手工冒烟（可选）：设置 `kiwi.bpm.slurm.max-concurrent-jobs=1`，启动两个并发流程，验证第二个任务出现 `WARN Slurm overloaded` 且 ~30s 后被重试拉起
