## Why

Slurm 集成在路径解析、失败时读 stderr 文件以及生成 sbatch 脚本时存在可被滥用的高风险点：能影响流程变量或相关配置的主体可能诱导服务端读取工作目录外文件、向脚本注入额外行，或与集群日志路径不一致导致误操作。需要在组件层收紧信任边界，避免「看起来像运维工具」却暴露主机文件系统或绕过 Slurm 边界。

## What Changes

- **stderr / stdout 路径**：相对路径必须落在配置的 Slurm 工作目录内（规范化后校验）；禁止任意绝对路径读失败详情文件（或仅限落在该目录下的绝对路径）。
- **失败路径读文件**：仅允许读取经校验位于允许目录内的路径；拒绝 traversal（`..` 等）。
- **sbatch 脚本生成**：对写入 `#SBATCH` 行与用户命令体的可控字符串进行规范化（至少禁止裸换行注入额外指令），降低脚本内容被流程变量篡改的风险。
- 增补单元测试覆盖路径规范化与非法路径拒绝。
- **BREAKING**：若历史上依赖「stderr 指向 Slurm 工作目录以外任意绝对路径」的失败上报语义，将在收紧后不再支持（须改为工作目录下路径或使用运维侧链接）。

## Capabilities

### New Capabilities

- `slurm-security-boundaries`: Slurm 组件对工作目录、日志路径与失败时读取文件的约束，以及 sbatch 写入的安全下限。

### Modified Capabilities

- （无）既有 `openspec/specs/slurm-workdir-cleanup` 描述清理策略；本次不修改其需求条目，仅实现上与「仅在工作目录内操作」保持一致。

## Impact

- **代码**：`kiwi-bpmn-component` 包 `com.kiwi.bpmn.component.slurm`（`SlurmService`、`SlurmJobCompleteProcessor`、`SbatchConfig` / `SlurmSbatchConfigBuilder` 等）。
- **部署**：运行 BPM 引擎的主机与 Slurm 集群节点若路径假设不一致，需在配置上对齐工作目录或挂载。
- **调用方**：依赖「 stderr 绝对路径任意指向」的流程须改为工作目录内路径。
