package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import org.apache.commons.io.FileUtils;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class SlurmService implements InitializingBean
{
    /**
     * 与 {@link SlurmExternalTaskHandler} 上 {@code @ExternalTaskSubscription(lockDuration = 300000)} 一致（毫秒）。
     */
    public static final long SLURM_TOPIC_LOCK_DURATION_MS = 300_000L;

    protected final SlurmProperties slurmProperties;
    private File shellFileDir;

    public SlurmService(SlurmProperties slurmProperties) {
        this.slurmProperties = slurmProperties;
    }

    /**
     * sacct 跟踪窗口时长（毫秒）：优先流程变量 {@code task_max_time}（秒，与外部任务 lock 延长语义一致），
     * 否则为 {@link #SLURM_TOPIC_LOCK_DURATION_MS} 减 {@link SlurmProperties#getExpirationExternalTaskLockDeltaMs()}，
     * 再否则为 {@link SlurmProperties.Sacct#getMaxTrackDurationMs()}
     * （下限为 {@link SlurmProperties#getExpirationExternalTaskLockDeltaMs()} 与 {@link SlurmProperties.Sacct#getMaxTrackDurationMs()} 的较大值）。
     */
    public long getSlurmJobMaxDuration(DelegateExecution execution) {
        Optional<Double> taskMaxSec =
                execution == null
                        ? Optional.empty()
                        : ExecutionUtils.getNumberInputVariable(execution, "task_max_time");
        long taskMaxMs =
                taskMaxSec
                        .filter(s -> s > 0)
                        .map(s -> Math.round(s * 1000.0))
                        .orElse(0L);
        if (taskMaxMs > 0) {
            return taskMaxMs;
        }
        long delta = slurmProperties.getExpirationExternalTaskLockDeltaMs();
        long durationFromTopicLock = SLURM_TOPIC_LOCK_DURATION_MS - delta;
        if (durationFromTopicLock > 0) {
            return durationFromTopicLock;
        }
        SlurmProperties.Sacct sacct = slurmProperties.getSacct();
        long sacctMs = sacct != null ? sacct.getMaxTrackDurationMs() : 168L * 3600_000L;
        return Math.min(SLURM_TOPIC_LOCK_DURATION_MS, sacctMs);
    }

    public File getShellFileDir() {
        return shellFileDir;
    }

    /**
     * 将 stdout/stderr 路径解析到 {@link #getShellFileDir()} 下。
     * 相对路径（含仅文件名）会拼到该目录；已是绝对路径则不变。
     */
    public String resolvePathUnderShellDir(String pathOrName) {
        if (pathOrName == null || pathOrName.isBlank()) {
            return pathOrName;
        }
        File f = new File(pathOrName);
        if (f.isAbsolute()) {
            return f.getAbsolutePath();
        }
        return new File(shellFileDir, pathOrName).getAbsolutePath();
    }

    public File createSbatchFile(String fileName, SbatchConfig sbatchConfig) {
        String cmd = sbatchConfig.getCommand();
        if (cmd == null || cmd.isBlank()) {
            throw new IllegalArgumentException("SbatchConfig.command is required");
        }
        File dir = shellFileDir;
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File sbatchFile = new File(dir, fileName);
        try {
            FileUtils.writeStringToFile(sbatchFile, "#!/bin/bash\n\n", StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(sbatchFile, sbatchConfig.toSbatchCmd() + "\n\n", StandardCharsets.UTF_8, true);
            // 用户命令直接写入脚本；作业退出码以 sacct .batch 的 ExitCode 为准（见 SlurmJobTracker）
            FileUtils.writeStringToFile(sbatchFile, cmd, StandardCharsets.UTF_8, true);
            FileUtils.writeStringToFile(sbatchFile, "\n", StandardCharsets.UTF_8, true);
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        return sbatchFile;
    }

    /**
     * @deprecated 已弃用 .flag 机制，请使用 sacct 跟踪（{@link SlurmJobTracker}）。
     */
    @Deprecated
    public String getFlagFilePath(String jobId) {
        return shellFileDir.getAbsolutePath() + "/" + jobId + ".flag";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.shellFileDir = new File(slurmProperties.getWorkDirectory());
        if (!this.shellFileDir.exists()) {
            this.shellFileDir.mkdirs();
        }
    }
}
