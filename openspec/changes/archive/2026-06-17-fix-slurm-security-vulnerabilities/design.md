## Context

`kiwi-bpmn-component` 的 Slurm 集成通过流程变量生成 sbatch、解析 stdout/stderr 路径，并在作业失败时读取 stderr 文件内容用于 Camunda `handleFailure`。当前实现允许绝对路径直接用于 `--output`/`--error` 与失败读文件，且相对路径未强制落在配置的工作目录内；`SbatchConfig` 将流程变量拼入脚本时未抑制换行注入。信任边界为「能设置流程变量或相关配置的主体」，需在服务端收紧默认行为。

## Goals / Non-Goals

**Goals:**

- 所有用于 Slurm 日志与失败读取的路径，在规范化后必须位于配置的 `workDirectory` 之下（或策略明确允许的等价根路径）。
- 失败路径读文件前做拒绝规则（traversal、越界绝对路径）。
- sbatch 生成时对写入脚本的控制字段做最低限度的注入防护（禁止裸 `\n`/`\r` 污染 `#SBATCH` 与命令块语义）。
- 可测试：路径规范化与拒绝逻辑可通过单元测试覆盖。

**Non-Goals:**

- 不在此变更中重做 Camunda 权限模型或流程发布审批。
- 不替代 Slurm 集群自身的配额与分区策略。
- 不要求支持「任意主机路径」作为 stderr 的企业特性（若将来需要，应单独规格化并由配置显式放行）。

## Decisions

1. **路径锚定**：以 `SlurmProperties#getWorkDirectory()` 解析为规范化目录（如 `Path.toRealPath` 在可行时），所有 `--output`/`--error` 及读文件路径均相对于该根目录解析并校验 `startsWith(root)`；拒绝则抛出明确业务/非法参数异常（或返回统一错误），避免静默改写为意外路径。
   - *备选*：仅警告并强行拼接到工作目录——拒绝静默改写，以免运维误判日志位置。

2. **绝对路径**：默认禁止指向工作目录外的绝对路径用于日志与失败读文件；若配置将来扩展「额外允许根」，单独规格（本次不实现）。

3. **换行注入**：对写入 `#SBATCH` 行的值与用户命令前的预处理：移除或替换 `\r`/`\n`，或对脚本采用「命令部分由 heredoc 包裹」等更强策略——本次采用**替换为空白或拒绝含换行字段**中的较轻量策略，优先实现成本与兼容性平衡。

4. **API 表面**：`resolvePathUnderShellDir` 等行为变更可能影响生成的 sbatch 内容；对外 Camunda 变量名不变，语义收紧。

## Risks / Trade-offs

- **[Risk]** 合法用户曾使用工作目录外的 stderr 绝对路径 → **Mitigation**：proposal 中标注 BREAKING；发布说明要求迁移到工作目录或符号链接。
- **[Risk]** `toRealPath` 在目录不存在或权限不足时失败 → **Mitigation**：回退到规范化 `normalize()` + 字符串前缀校验，并记录日志。
- **[Risk]** 过度严格导致部分流程失败 → **Mitigation**：错误消息明确指出路径策略与配置键。

## Migration Plan

1. 在测试/预发校验典型流程的 `slurm_output_file` / `slurm_error_file` 是否在工作目录下。
2. 发布说明列出 BREAKING 与配置检查项。
3. 若异常增多，临时可通过运维将工作目录设为更宽的挂载父路径（仍优于任意绝对路径）。

## Open Questions

- 是否需要配置开关「允许 stderr 绝对路径（不推荐）」——建议默认关闭，若产品坚持再开变更。
