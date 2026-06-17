# Tasks

- [x] `SlurmJob` @Document + `SlurmJobRepository`；Tracker 仅 Mongo 注入
- [x] `SlurmAutoConfiguration` + Mongo 条件装配；启动校验
- [x] 默认关闭/移除 flag 监听；弃用 sbatch 写 flag
- [x] sacct 轮询终态；submit 后 `SlurmJob` 落库
- [x] 精简 `SlurmService` 脚本（无 flag 耦合）
- [x] `pom` 显式 mongodb 依赖；Mock Repository 单测

## 与 plan 命名对照

- [x] `SlurmJobCompletionTracker` → **`SlurmJobTracker`**
- [x] 删除 `SlurmTrackedExternalJob`（逻辑并入 `SlurmJob`）
