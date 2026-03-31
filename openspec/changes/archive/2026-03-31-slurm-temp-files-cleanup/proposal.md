## Why

Slurm 集成在 `kiwi.bpm.slurm` 工作目录下持续生成 `.sbatch` 脚本、标准输出/错误日志、`.flag` / `.flag.done` 等临时或中间文件。长期运行后目录会无限增长，占满磁盘并增加备份与排查成本。需要在**可配置保留策略**下自动删除**已过期**的此类文件。

## What Changes

- 增加 **Slurm 工作目录临时文件自动清理**能力：按文件年龄（或固定保留时长）删除过期文件，避免删除“可能仍被正在运行的作业使用”的文件（通过命名约定与安全规则界定）。
- 在 **`SlurmProperties`（或等价配置）** 中增加清理相关配置项（例如是否启用、保留时长、Cron/固定延迟等），默认保守（关闭或较长保留期），避免 **BREAKING** 行为。
- 实现侧采用 **Spring 调度**（`@Scheduled`）或等价机制，在 `kiwi-bpmn-component` 的 Slurm 模块内注册为条件 Bean（仅在启用 Slurm 工作目录配置时生效）。

## Capabilities

### New Capabilities

- `slurm-workdir-cleanup`: 定义 Slurm 工作目录下过期临时文件的自动清理行为、配置项与安全约束。

### Modified Capabilities

- （无；`openspec/specs/` 下无存量规格。）

## Impact

- **后端**：`kiwi-bpmn-component` 中 `SlurmProperties`、`SlurmAutoConfiguration`、新增清理调度类（或并入 `SlurmService`/`SlurmTaskManager` 的专职组件）。
- **配置**：`application.yml` / 环境变量文档中增加可选配置说明。
- **运维**：需理解保留策略；错误配置可能导致过早删除仍在观察期内的文件，故需提供清晰默认值与日志。
