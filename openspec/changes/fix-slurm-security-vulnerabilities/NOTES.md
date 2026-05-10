# Release / BREAKING（stderr / stdout 路径）

自本变更起，`slurm_output_file` / `slurm_error_file` 解析后必须落在 `kiwi.bpm.slurm.work-directory` 之下；失败时读取 stderr 同样受此约束。此前依赖「任意主机绝对路径」的流程需改为工作目录内路径或等价挂载。详见 `proposal.md`。
