package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class SlurmService implements InitializingBean
{
    /**
     * 与 {@link SlurmExternalTaskHandler} 上 {@code @ExternalTaskSubscription(lockDuration = 300000)} 一致（毫秒）。
     */
    public static final long SLURM_TOPIC_LOCK_DURATION_MS = 900_000L;

    protected final SlurmProperties slurmProperties;
    private File shellFileDir;

    /** 规范化后的工作目录根路径；用于校验 stdout/stderr 及失败读文件路径不得越界。 */
    private Path workDirectoryRoot;

    public SlurmService(SlurmProperties slurmProperties) {
        this.slurmProperties = slurmProperties;
    }

    /**
     * sacct / Mongo {@link SlurmJob#getExpiration()} 跟踪窗口时长（毫秒），
     * 与 {@link #getExternalTaskLockExtensionDurationMs} 共用同一基准时长（后者再叠加 delta）。
     * <p>
     * 优先流程变量 {@code task_max_time}（秒）；否则为 {@link #SLURM_TOPIC_LOCK_DURATION_MS} 减
     * {@link SlurmProperties#getExpirationExternalTaskLockDeltaMs()}（若为正）；否则回落为
     * {@link SlurmProperties.Sacct#getMaxTrackDurationMs()}（未配置 sacct 节时为 168h）。
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
        return sacct != null ? sacct.getMaxTrackDurationMs() : 900_000L;
    }

    /**
     * External Task {@code extendLock} 使用的延长时间（毫秒）：与 {@link #getSlurmJobMaxDuration} 同基准；
     * 当 {@link SlurmProperties#getExpirationExternalTaskLockDeltaMs()} &gt; 0 时，比跟踪窗口（亦即相对创建时刻到
     * {@link SlurmJob#getExpiration()} 的时长）多出该 delta，便于终态上报收尾。
     */
    public long getExternalTaskLockExtensionDurationMs(DelegateExecution execution) {
        long baseMs = getSlurmJobMaxDuration(execution);
        long deltaMs = slurmProperties.getExpirationExternalTaskLockDeltaMs();
        if (deltaMs > 0) {
            return baseMs + deltaMs;
        }
        return baseMs;
    }

    public File getShellFileDir() {
        return shellFileDir;
    }

    /**
     * 将 stdout/stderr 路径解析到配置的 Slurm 工作目录下：相对路径相对于该目录；绝对路径也必须落在该目录之内。
     *
     * @throws IllegalArgumentException 路径逃逸工作目录（含 {@code ..}）或指向目录外绝对路径
     */
    public String resolvePathUnderShellDir(String pathOrName) {
        if (pathOrName == null || pathOrName.isBlank()) {
            return pathOrName;
        }
        return resolvePathContained(pathOrName.trim()).toString();
    }

    /**
     * 判断给定路径（相对工作目录或绝对路径）解析后是否仍落在配置的工作目录下；用于失败时是否允许读 stderr 文件。
     */
    public boolean isResolvedPathUnderWorkDirectory(String pathOrName) {
        if (pathOrName == null || pathOrName.isBlank()) {
            return false;
        }
        try {
            resolvePathContained(pathOrName.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Path resolvePathContained(String pathOrName) {
        Path root = requireWorkRoot();
        Path candidate;
        Path raw = Path.of(pathOrName);
        if (raw.isAbsolute()) {
            candidate = raw.normalize();
        } else {
            candidate = root.resolve(raw).normalize();
        }
        candidate = candidate.toAbsolutePath();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Slurm path must stay under work directory ("
                            + root
                            + "), configured key kiwi.bpm.slurm.work-directory: "
                            + pathOrName);
        }
        return candidate;
    }

    private Path requireWorkRoot() {
        if (workDirectoryRoot == null) {
            throw new IllegalStateException("SlurmService work directory not initialized");
        }
        return workDirectoryRoot;
    }

    public File createSbatchFile(String fileName, SbatchConfig sbatchConfig) {
        String cmd = SlurmScriptSanitizeUtils.stripEmbeddedNewlines(sbatchConfig.getCommand());
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
        if (StringUtils.isBlank(slurmProperties.getWorkDirectory())) {
            throw new IllegalStateException(
                    "kiwi.bpm.slurm.work-directory is required when kiwi.bpm.slurm.enabled=true");
        }
        this.shellFileDir = new File(slurmProperties.getWorkDirectory());
        if (!this.shellFileDir.exists()) {
            this.shellFileDir.mkdirs();
        }
        this.workDirectoryRoot = normalizeWorkRoot(this.shellFileDir);
    }

    /**
     * 优先 {@link Path#toRealPath()}；目录不存在或解析失败时退回规范化绝对路径。
     */
    private Path normalizeWorkRoot(File dir) {
        Path base = dir.toPath().toAbsolutePath().normalize();
        if (!Files.exists(base)) {
            return base;
        }
        try {
            return base.toRealPath();
        } catch (IOException e) {
            log.warn(
                    "Could not resolve real path for Slurm work directory {}, using normalized path: {}",
                    base,
                    e.toString());
            return base;
        }
    }
}
