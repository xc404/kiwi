## Context

Slurm 相关文件当前写入 `SlurmService` / `SlurmProperties` 所配置的**单一工作目录**（脚本、日志、flag 等）。无内置清理时，该目录随时间与任务量线性增长。

## Goals / Non-Goals

**Goals:**

- 在可配置策略下**定期**扫描工作目录，删除**超过保留时间**的候选文件。
- 默认策略**保守**（易预测的保留时长、可关闭），并输出可观测日志（删除数量、路径摘要、错误）。
- 与现有 Slurm Bean 条件装配一致：仅在 Slurm 工作目录等配置存在且启用清理时注册调度任务。

**Non-Goals:**

- 不实现集群侧 `sacct`/`squeue` 与本地文件的强一致（不以 Slurm 调度器为唯一真相源做“作业是否结束”判定）。
- 不在此变更中迁移工作目录结构或重命名现有文件约定。
- 不提供按“单作业 ID”精确回收的完整 GC（可作为后续增强）。

## Decisions

1. **调度方式**：使用 Spring `@Scheduled`（`fixedDelayString` 或 `cron` 由配置注入），Bean 使用 `@EnableScheduling` 在 Slurm 自动配置或应用入口条件启用，避免无 Slurm 时空跑。
2. **保留策略**：以**文件最后修改时间**（`File.lastModified()`）相对当前时间判断是否过期；保留时长由配置项 `Duration` 或整型“天数/小时”表达，文档中写清单位。
3. **删除范围**：仅删除工作目录**根目录或约定子路径**下、匹配扩展名/后缀的文件（例如 `.sbatch`、`.out`、`.err`、`.flag`、`.flag.done`），**不递归**或**可配置是否递归**，默认**仅一层**以降低误删深层用户数据风险。
4. **安全边界**：不删除目录本身；可选**最小文件年龄**（例如必须早于 N 分钟）防止与刚写入文件竞态；对删除失败单条记录 warn，不中断整批。
5. **配置位置**：扩展 `SlurmProperties`（`kiwi.bpm.slurm.cleanup.*`）：`enabled`、`retention`、调度周期、可选 `patterns`/`recursive`。
6. **替代方案**：操作系统 `tmpwatch`/cron 脚本 — 被拒，因与 Spring 配置生命周期割裂且难在测试中验证；纯启动时一次性清理 — 不足以覆盖长期运行。

## Risks / Trade-offs

- **[Risk]** 保留时间过短导致仍被人工查看的日志被删 → **Mitigation**：默认较长保留期、文档醒目说明、关键路径打 INFO 日志。
- **[Risk]** 与 `FileAlterationMonitor` 并发读写同一文件 → **Mitigation**：最小年龄窗口 + 仅删过期文件；flag 处理完成后文件常被删除或改名，冲突面小。
- **[Trade-off]** 基于 mtime 而非业务“作业完成”时间 → 接受；简单可测，与“临时文件”语义一致。

## Migration Plan

- 部署新版本后默认 **cleanup.enabled=false** 或 **保留期足够长**，由运维显式收紧。
- 回滚：关闭配置即停止删除；已被删文件无法自动恢复，需备份策略兜底。

## Open Questions

- 是否在首版支持**递归子目录**（默认可关闭）；若业务将日志写到嵌套目录，可在后续迭代打开。
