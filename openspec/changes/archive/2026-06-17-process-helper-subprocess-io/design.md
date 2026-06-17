## Context

Java `Process` 的管道容量有限（常见约 64KB 量级）。父进程必须先消费子进程写入的数据，或在与 `waitFor` 并发执行的线程中排空流，否则易出现「子进程阻塞在写、父进程阻塞在等」的经典死锁。合并 stderr 到 stdout 时，仅需排空 `Process#getInputStream()`；未合并时必须**并行**排空 stdout 与 stderr 两路管道。

## Goals / Non-Goals

**Goals:**

- 提供单一入口 `ProcessHelper.waitForDrain`，封装排空顺序、`waitFor`/`waitFor(timeout)`、超时销毁、读线程 `join` 与错误传播。
- 调用方传入的 `mergedErrorStream` **MUST** 与创建该 `Process` 时 `ProcessBuilder#redirectErrorStream(boolean)` 的实际设置一致。

**Non-Goals:**

- 不规定子进程命令安全策略（仍由调用方负责）。
- 不替代 `ProcessBuilder` 的目录、环境变量等配置。

## Decisions

1. **放置模块**：`kiwi-common`，供 admin 与 bpmn 组件共用。
2. **API 形态**：`StreamResult(exitCode, stdout, stderr)`；合并 stderr 时 `stderr` 为空数组。
3. **超时语义**：`timeout <= 0` 表示对 `Process.waitFor()` 无限等待（仍先启动排空线程）；超时则 `destroyForcibly()` 并抛出 `TimeoutException`。
4. **字符集**：工具类仅返回字节；调用方（如 CLI help、Shell）自行选择解码字符集。

## Risks / Trade-offs

- **[Risk]** 极大输出全部读入内存 → **Mitigation**：与原先「读入字符串」行为一致；若未来需流式处理，可另增 API。
- **[Trade-off]** 读线程 `join` 使用上限时间，极端慢速 I/O 下可能仍提前返回已缓冲数据；进程已退出后通常足够。

## Migration Plan

- 无数据迁移；行为对调用方为内部重构，对外部 API（如 REST）无破坏性变更。

## Open Questions

- 无。
