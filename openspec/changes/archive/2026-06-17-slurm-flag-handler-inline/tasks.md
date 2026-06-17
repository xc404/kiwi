# Tasks

## Plan 草案（未实施 — 已被 sacct 迁移取代）

- [ ] 将 flag 相关实现迁入 `SlurmFlagFileHandler` 私有方法；构造器接收 Spring 依赖
- [ ] `SlurmAutoConfiguration` 声明 `@Bean SlurmFlagFileHandler`
- [ ] `SlurmTaskManager` 构造注入 handler；`startFlagWatcher` 使用 Bean 注册 listener
- [ ] 编译验证

## 后续已落地（见 `2026-06-17-slurm-job-mongo-only`）

- [x] 移除 `.flag` 监听与 `SlurmFlagFileHandler`
- [x] `SlurmJobTracker` + `SlurmJobCompleteProcessor` sacct 终态路径
