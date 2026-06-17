## ADDED Requirements

### Requirement: ProcessHelper 排空子进程管道并等待退出

系统 SHALL 在 `kiwi-common` 中提供 `com.kiwi.common.process.ProcessHelper`，并 SHALL 提供静态方法 `waitForDrain(Process process, boolean mergedErrorStream, long timeout, TimeUnit unit)`，其语义如下。

实现 **SHALL** 在调用 `Process.waitFor()` 或 `Process.waitFor(long, TimeUnit)` **之前或同时**，通过独立线程从子进程管道读取数据，以避免子进程因写满管道缓冲区而阻塞、从而导致父进程无法观察到进程结束的死锁。

当 `mergedErrorStream` 为 **true** 时，实现 **SHALL** 仅排空 `Process#getInputStream()`（与已通过 `ProcessBuilder#redirectErrorStream(true)` 将 stderr 合并进 stdout 的用法一致），且 **SHALL NOT** 假定 `Process#getErrorStream()` 可读。当 `mergedErrorStream` 为 **false** 时，实现 **SHALL** 同时排空标准输出与标准错误流（若错误流非 null），且 **SHALL** 以并发读取方式避免两路管道之一被写满而阻塞子进程。

`timeout` **SHALL** 与 `unit` 共同解释：若 `timeout` 小于或等于零，实现 **SHALL** 使用无限等待直至子进程结束（仍 **SHALL** 先按上文规则排空管道）。若 `timeout` 大于零，实现 **SHALL** 在指定时间内等待子进程结束；若超时，实现 **SHALL** 调用 `Process#destroyForcibly()` 尝试终止子进程，并 **SHALL** 抛出 `java.util.concurrent.TimeoutException`（或等价可区分超时的错误），且 **SHALL** 在合理时间内尝试汇合读线程以避免资源泄漏。

方法返回类型 **SHALL** 包含子进程退出码及标准输出、标准错误的字节内容；当 stderr 已合并进 stdout 时，返回体中的 stderr 字节 **SHALL** 为空数组。

若读取流时发生 `IOException`，实现 **SHALL** 以受检异常或包装形式向上抛出，且 **SHALL NOT** 静默吞没导致调用方误以为成功。

#### Scenario: 合并 stderr 时仅排空单一合并流

- **WHEN** 创建 `Process` 时使用了 `redirectErrorStream(true)`，且调用 `waitForDrain` 时 `mergedErrorStream` 为 true
- **THEN** 实现 **SHALL** 仅从合并后的输入流读取，且返回的 stderr 部分 **SHALL** 为空字节序列

#### Scenario: 未合并时并行排空两路流

- **WHEN** `mergedErrorStream` 为 false 且标准错误流可用
- **THEN** 实现 **SHALL** 同时消费标准输出与标准错误，避免仅等待其中一路而导致另一路塞满

---

### Requirement: 业务代码通过 ProcessHelper 等待子进程

以下组件 **SHALL** 使用 `ProcessHelper.waitForDrain`（或后续与之等价的官方封装）获取子进程退出码与输出字节，**SHALL NOT** 在仅调用 `Process.waitFor()` 后再首次读取整段 stdout/stderr 作为唯一消费方式（除非文档声明输出量极小且已审阅风险）。

- `com.kiwi.project.bpm.utils.CliHelpParser` 中执行 help 命令的路径  
- `com.kiwi.bpmn.component.activity.ShellActivityBehavior` 中在 `waitFlag` 为真时等待子进程的路径（`mergedErrorStream` **SHALL** 与 `ProcessBuilder#redirectErrorStream` 的实际参数一致）  
- `com.kiwi.bpmn.component.slurm.SlurmTaskManager` 中提交 `sbatch` 的路径（未合并 stderr 时 **SHALL** 使用 `mergedErrorStream` 为 false）

#### Scenario: Shell 与 redirectErrorStream 一致

- **WHEN** Shell 活动将 `redirectErrorStream` 设为与 `ProcessBuilder` 一致
- **THEN** 传入 `waitForDrain` 的 `mergedErrorStream` **SHALL** 与该标志相同，以保证排空策略与管道拓扑一致
