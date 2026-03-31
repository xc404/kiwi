# Slurm 工作目录临时文件清理

在 `kiwi.bpm.slurm.workDirectory` 已配置的前提下，可通过 `kiwi.bpm.slurm.cleanup` 自动删除过期临时文件（`.sbatch`、标准输出/错误、`.flag` / `.flag.done` 等）。

## 默认行为

- **`cleanup.enabled`**：默认 `false`，不执行删除；启用前请确认保留时长符合运维要求。
- **`cleanup.retention-ms`**：默认 7 天（604800000 ms），仅当「当前时间 − 最后修改时间」**大于等于**该值且满足 `min-age` 时才删除。
- **`cleanup.min-age-ms`**：默认 60 秒，避免刚写入的文件被误删。
- **`cleanup.fixed-delay-ms`**：默认 1 小时，两次清理任务之间的间隔。
- **`cleanup.recursive`**：默认 `false`，仅扫描工作目录**根下**文件；设为 `true` 时递归子目录。
- **`cleanup.suffixes`**：默认可删除后缀见 `SlurmWorkdirCleanup.DEFAULT_SUFFIXES`；非空时仅匹配列表中的后缀。

## 示例配置（YAML）

```yaml
kiwi:
  bpm:
    slurm:
      workDirectory: /data/slurm-work
      cleanup:
        enabled: true
        retention-ms: 604800000
        min-age-ms: 60000
        fixed-delay-ms: 3600000
        recursive: false
```

## 运维注意

- 过短的 `retention-ms` 可能导致仍在查阅的日志被删；生产环境建议从较长保留期开始，观察日志再收紧。
- 清理任务打 INFO 日志（删除数量、失败数）；单文件删除失败会 WARN 并继续其余文件。
