## Why

在父进程仅调用 `Process.waitFor()`（或限时 `waitFor`）且**不在其他线程持续读取**子进程标准输出/标准错误时，子进程若向管道写入超过操作系统缓冲区容量的数据，会在写端阻塞；子进程无法结束，父进程便永远等不到退出，表现为**死锁**或**超时无效**。此前 `CliHelpParser`、Shell 活动、`sbatch` 提交等处存在该风险或已用手写读线程局部规避，缺少统一抽象与规格约束。

## What Changes

- 在 `kiwi-common` 新增 `com.kiwi.common.process.ProcessHelper`，提供 `waitForDrain`：在后台线程排空 stdout（及未合并时的 stderr），再等待进程结束；支持限时等待、超时销毁与读线程汇合。
- `CliHelpParser`、Shell 活动行为、`SlurmTaskManager#submitSbatch` 改为通过 `ProcessHelper` 获取子进程输出与退出码，避免重复实现与遗漏。

## Capabilities

### New Capabilities

- `process-helper-subprocess-io`：定义子进程流排空与等待的契约及调用侧要求（与 `ProcessBuilder#redirectErrorStream` 一致性等）。

### Modified Capabilities

- （与 CLI help 生成相关的既有 change 可独立存在；本 change 聚焦进程 IO 工具与调用迁移。）

## Impact

- **kiwi-common**：新增 `ProcessHelper`（无新外部依赖）。
- **kiwi-admin**：`CliHelpParser` 依赖 `ProcessHelper`。
- **kiwi-bpmn-component**：`ShellActivityBehavior`、`SlurmTaskManager` 依赖 `ProcessHelper`（已有对 `kiwi-common` 的依赖）。
