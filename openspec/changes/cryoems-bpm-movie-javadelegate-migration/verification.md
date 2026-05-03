## DoD 与演练清单

### 0) 迁移梳理结果（对应 tasks 1.1 ~ 1.4）

#### 0.1 首批步骤映射（1.1）

- `HEADER` → `movieHeaderJavaDelegate`
- `MOTION_CORRECTION` → `movieMotionCorrectionJavaDelegate`
- `CTF_ESTIMATION` → `movieCtfEstimationJavaDelegate`

说明：
- 三个步骤已绑定到 `cryo-movie-minimal.bpmn`，形成 `Header -> Motion -> CTF` 顺序链路。
- 其余旧 Handler 步骤仍保留兼容路径，后续分批迁移。

#### 0.2 流程输入契约（1.2）

总入口变量（由 `MovieKiwiWorkflowService` 侧注入）：
- `movie`（必填）：至少包含 `file_path`、`file_name`，可含 `forceReset`
- `task`（必填）：至少包含 `id`、`microscope`、`taskSettings`
- `taskDataset`（可选）：建议包含 `gain0.usable_path`、`taskDataSetSetting.p_size/total_dose_per_movie`

可选运行时变量（用于增强执行）：
- `motionWorkDir`、`ctfWorkDir`、`imageWorkDir`
- `motionOutputFile` / `motionNoDwPath`（CTF 输入优先级）
- `forceReset`（流程级强制重跑）

#### 0.3 迁移边界与回退策略（1.3）

迁移边界：
- 当前仅迁移 `HEADER`、`MOTION_CORRECTION`、`CTF_ESTIMATION` 三个步骤到 JavaDelegate。
- `Motion/CTF` 输出以“批处理命令 + 关键产物路径”作为流程变量契约。

回退策略：
- 开关变量：`movieDelegateRollback=true`
- 行为：三个 delegate 均输出 `*Skipped=true` 与 `*SkipReason=rollback_enabled`，不生成命令变量。
- 验证：`MovieDelegatesPipelineTest#shouldSkipAllDelegatesWhenRollbackEnabled`

#### 0.4 执行职责归属（1.4）

- `kiwi-admin`：流程最终执行入口（流程定义加载、delegate 装配与运行）
- `cryoems-bpm`：提供 movie delegate 能力与命令产物映射
- `cyroems`：保留原有 Handler 与领域服务作为迁移参考与兼容路径

### 1) 功能完成定义（DoD）

- `kiwi-admin` 已依赖 `cryoems-bpm`，并能发现 `movieHeaderJavaDelegate`、`movieMotionCorrectionJavaDelegate`、`movieCtfEstimationJavaDelegate`。
- `cryo-movie-minimal.bpmn` 已绑定上述 3 个 delegate。
- JavaDelegate 输出采用平铺变量，不再传递 `StepResult` 对象。
- Motion/CTF delegate 输出批处理命令与关键产物路径（输出文件、日志文件）。
- 所有 delegate 的 `ComponentDescription` 符合约束：
  - 不重复声明入口统一注入变量到 `inputs`
  - `outputs.key` 使用业务字段名
  - `description` 仅描述类功能

### 2) 自动化验证（本地可重复）

- 编译：
  - `mvn -pl cryoems-bpm,kiwi-admin/backend -DskipTests compile`
- cryoems-bpm 单测：
  - `mvn -pl cryoems-bpm test`
- kiwi-admin 装配测试（需 reactor 一起跑）：
  - `mvn -pl cryoems-bpm,kiwi-admin/backend test -Dtest=CryoMovieProcessWiringTest "-Dsurefire.failIfNoSpecifiedTests=false"`

### 3) 回退开关演练（movieDelegateRollback）

- 开启方式：流程变量 `movieDelegateRollback=true`
- 预期行为：
  - Header/Motion/CTF delegate 均输出 `*Skipped=true`
  - `*SkipReason=rollback_enabled`
  - Motion/CTF 不生成批处理命令变量（例如 `motionCommand`、`ctfCommand` 为空）
- 现有覆盖：
  - `MovieDelegatesPipelineTest#shouldSkipAllDelegatesWhenRollbackEnabled`

### 4) 预发布验收建议（人工）

- 在预发布环境以真实 `movie/task/taskDataset` 启动流程实例。
- 检查流程变量中命令与产物路径是否完整。
- 对异常场景（缺少 `movie.file_path`、参数缺失）确认错误变量可观测。
- 执行一次回退开关演练并确认流程可安全跳过新 delegate 节点。
