package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.process.ProcessHelper;
import com.kiwi.bpmn.external.ExternalTaskExecution;
import com.kiwi.bpmn.external.config.ClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Slurm 任务提交（sbatch）与作业目录下 .flag 文件监听（驱动 External Task 完成）。
 */
@Slf4j
public class SlurmTaskManager implements InitializingBean
{

    private final SlurmProperties slurmProperties;
    private final SlurmService slurmService;
    private final ProcessEngine processEngine;
    private final ExternalTaskService externalTaskService;
    private final ObjectProvider<ClientProperties> clientProperties;

    private ThreadPoolTaskExecutor taskExecutor;
    private final FileAlterationMonitor flagFileMonitor = new FileAlterationMonitor(1000);
    private volatile boolean watcherRunning;

    public SlurmTaskManager(
            SlurmProperties slurmProperties,
            SlurmService slurmService,
            ProcessEngine processEngine,
            ObjectProvider<ClientProperties> clientProperties) {
        this.slurmProperties = slurmProperties;
        this.slurmService = slurmService;
        this.processEngine = processEngine;
        this.externalTaskService = processEngine.getExternalTaskService();
        this.clientProperties = clientProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        int poolSize = slurmProperties.getThreadPoolSize();
        this.taskExecutor = new ThreadPoolTaskExecutor();
        this.taskExecutor.setCorePoolSize(poolSize);
        this.taskExecutor.setMaxPoolSize(poolSize);
        this.taskExecutor.initialize();
        log.info("SlurmTaskManager thread pool initialized: core=max={}", poolSize);
        String effective = effectiveExternalTaskWorkerId();
        if (effective != null) {
            String source =
                    (slurmProperties.getExternalTaskWorkerId() != null
                                    && !slurmProperties.getExternalTaskWorkerId().isBlank())
                            ? "kiwi.bpm.slurm.external-task-worker-id"
                            : "kiwi.bpm.external-task.worker-id (ClientProperties)";
            log.info("Slurm flag files will only be processed when workerId matches: {} (from {})", effective, source);
        } else {
            log.info(
                    "Slurm worker filter off: set kiwi.bpm.slurm.external-task-worker-id or kiwi.bpm.external-task.worker-id to restrict .flag handling");
        }
    }

    /**
     * 优先 {@link SlurmProperties#getExternalTaskWorkerId()}；为空则使用外部任务 client 的
     * {@link ClientProperties#getWorkerId()}（与拉锁/complete 的 worker 一致）。
     */
    private String effectiveExternalTaskWorkerId() {
        String fromSlurm = slurmProperties.getExternalTaskWorkerId();
        if (fromSlurm != null && !fromSlurm.isBlank()) {
            return fromSlurm.trim();
        }
        ClientProperties cp = clientProperties.getIfAvailable();
        if (cp != null && cp.getWorkerId() != null && !cp.getWorkerId().isBlank()) {
            return cp.getWorkerId().trim();
        }
        return null;
    }

    /**
     * 启动对 {@link SlurmService#getShellFileDir()} 下 <code>*.flag</code> 的监听（幂等）。
     */
    public synchronized void startFlagWatcher() {
        try {
            if( watcherRunning ) {
                log.debug("Slurm flag watcher already running, skip start");
                return;
            }
            flagFileMonitor.start();
            File dir = slurmService.getShellFileDir();
            FileAlterationObserver observer = FileAlterationObserver.builder()
                    .setFile(dir)
                    .setFileFilter(new SuffixFileFilter("flag"))
                    .get();
            observer.addListener(new SlurmFlagFileHandler());
            flagFileMonitor.addObserver(observer);
            this.watcherRunning = true;
            log.info("Slurm flag watcher started on directory: {}", dir.getAbsolutePath());
        } catch( Exception e ) {
            log.error("Failed to start Slurm flag watcher", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据 {@code execution} 与 {@code sbatchConfig}（须已含 {@link SbatchConfig#getCommand()}）生成 sbatch 文件、启动 flag 监听并提交 sbatch。
     * 若为 {@link ExternalTaskExecution}，则在脚本末尾追加写入 {@code $SLURM_JOB_ID.flag} 的一行：
     * {@code 退出码,taskId,workerId,jobName}（退出码来自 {@link SlurmService} 中对用户 command 子 shell 的捕获）。
     * <p>
     * 返回的 {@link SlurmJob} 含作业 ID、作业名、脚本与日志路径；提交成功后会把完整结果写回 {@code execution}。
     */
    public CompletableFuture<SlurmJob> submitSlurmJob(DelegateExecution execution, SbatchConfig sbatchConfig) {
        File sbatchFile = slurmService.createSbatchFile(execution.getId() + ".sbatch", sbatchConfig);
//        writeSlurmPathsBeforeSubmit(execution, sbatchFile, sbatchConfig);

        log.info(
                "Preparing Slurm submit: processInstanceId={}, activityId={}, executionId={}, jobName={}, sbatchFile={}",
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId(),
                execution.getId(),
                sbatchConfig.getJobName(),
                sbatchFile.getAbsolutePath());

        startFlagWatcher();

        String externalTaskId = null;
        String workerId = null;
        String jobNameForFlag = null;
        if( execution instanceof ExternalTaskExecution ext ) {
            externalTaskId = ext.getExternalTask().getId();
            workerId = ext.getExternalTask().getWorkerId();
            jobNameForFlag = sbatchConfig.getJobName();
        }
        final String tid = externalTaskId;
        final String wid = workerId;
        final String jn = jobNameForFlag;
        if( tid != null && wid != null && jn != null ) {
            appendFlagCompletionToSbatch(sbatchFile, tid, wid, jn);
            log.debug("Appended flag-completion line to sbatch for externalTaskId={}, workerId={}, jobName={}", tid, wid, jn);
        } else {
            log.debug(
                    "Skipped flag-completion append (not ExternalTaskExecution or missing ids): executionId={}",
                    execution.getId());
        }
        return taskExecutor.submitCompletable(() -> {
            SlurmJob job = submitSbatch(sbatchFile);
            job.setJobName(sbatchConfig.getJobName());
            job.setSbatchFilePath(sbatchFile.getAbsolutePath());
            job.setOutputFilePath(sbatchConfig.getOutput_file());
            job.setErrorFilePath(sbatchConfig.getError_file());
            return job;
        });
    }


    private void appendFlagCompletionToSbatch(File sbatchFile, String taskId, String workerId, String jobName) {
        try {
            String line = "\n\n" + buildFlagCompletionShellLine(taskId, workerId, jobName);
            FileUtils.writeStringToFile(sbatchFile, line, StandardCharsets.UTF_8, true);
        } catch( IOException e ) {
            throw new RuntimeException("Failed to append flag completion to sbatch file", e);
        }
    }

    /**
     * 将 flag 内容写为一行：{@code 退出码,taskId,workerId,jobName}；{@code $__KIWI_SLURM_CMD_EC} 由 SlurmService 生成的脚本赋值。
     */
    private String buildFlagCompletionShellLine(String taskId, String workerId, String jobName) {
        String workDir = slurmService.getShellFileDir().getAbsolutePath();
        String outTarget = workDir.replace("\\", "/").replace("\"", "\\\"") + "/$SLURM_JOB_ID.flag";
        return "printf '%s,%s,%s,%s\\n' \"$__KIWI_SLURM_CMD_EC\" "
                + shellSingleQuoted(taskId) + " "
                + shellSingleQuoted(workerId) + " "
                + shellSingleQuoted(jobName) + " > \"" + outTarget + "\"\n";
    }

    /**
     * Bash 单引号字面量：{@code 'a'b' -> 'a'"'"'b'}
     */
    private static String shellSingleQuoted(String s) {
        if( s == null ) {
            return "''";
        }
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static final Pattern SIGNED_INT = Pattern.compile("-?\\d+");

    private static final class SlurmCmdResult
    {

        final int commandExitCode;
        final String taskId;
        final String workerId;
        final String jobName;

        SlurmCmdResult(int commandExitCode, String taskId, String workerId, String jobName) {
            this.commandExitCode = commandExitCode;
            this.taskId = taskId;
            this.workerId = workerId;
            this.jobName = jobName;
        }
    }

    /**
     * 新格式一行：{@code 退出码,taskId,workerId,jobName}；兼容旧格式：{@code taskId,workerId,jobName}（视为命令成功退出码 0）。
     */
    static SlurmCmdResult parseSlurmFlagFileContent(String raw) throws IOException {
        String text = raw == null ? "" : raw.trim();
        if( text.isEmpty() ) {
            throw new IOException("Slurm flag file is empty");
        }
        String[] parts = text.split(",", 4);
        if( parts.length >= 4 && SIGNED_INT.matcher(parts[0]).matches() ) {
            try {
                int ec = Integer.parseInt(parts[0]);
                return new SlurmCmdResult(ec, parts[1].trim(), parts[2].trim(), parts[3].trim());
            } catch( NumberFormatException e ) {
                throw new IOException("Invalid exit code in flag file: " + text, e);
            }
        }
        if( parts.length >= 3 ) {
            return new SlurmCmdResult(0, parts[0].trim(), parts[1].trim(), parts[2].trim());
        }
        throw new IOException("Invalid Slurm flag content (expected 3 or 4 comma-separated fields): " + text);
    }

    private SlurmJob submitSbatch(File batchFile) {
        batchFile.setExecutable(true);
        String scriptPath = batchFile.getAbsolutePath();
        ProcessBuilder processBuilder = new ProcessBuilder("sbatch", scriptPath);
        log.info("Executing sbatch: {}", scriptPath);
        try {
            Process process = processBuilder.start();
            ProcessHelper.StreamResult drained = ProcessHelper.waitForDrain(process, false, 0, TimeUnit.SECONDS);
            int exitCode = drained.exitCode();
            String message = new String(drained.stdout(), StandardCharsets.UTF_8);
            if( exitCode != 0 ) {
                String errorMessage = new String(drained.stderr(), StandardCharsets.UTF_8);
                log.warn("sbatch failed: exitCode={}, script={}, stderr={}", exitCode, scriptPath, errorMessage);
                throw new RuntimeException("sbatch command failed with exit code " + exitCode + ": " + errorMessage);
            }
            String[] parts = message.trim().split("\\s+");
            if( parts.length < 4 || !parts[0].equals("Submitted") || !parts[1].equals("batch") || !parts[2].equals("job") ) {
                log.warn("sbatch unexpected stdout: script={}, stdout={}", scriptPath, message.trim());
                throw new RuntimeException("Unexpected sbatch output: " + message);
            }
            String jobId = parts[3];
            log.info("sbatch succeeded: jobId={}, script={}", jobId, scriptPath);
            SlurmJob job = new SlurmJob();
            job.setJobId(jobId);
            return job;
        } catch( RuntimeException e ) {
            throw e;
        } catch( Exception e ) {
            log.error("sbatch invocation failed: script={}", scriptPath, e);
            throw new RuntimeException("Failed to submit Slurm job", e);
        }
    }

    /**
     * 调用 {@link ExternalTaskService#complete} 一次；成功返回 {@code true}，失败时把异常写入 {@code lastFailure[0]} 并返回 {@code false}。
     */
    private boolean handleComplete(SlurmCmdResult slurmCmdResult) {
        try {
            externalTaskService.complete(slurmCmdResult.taskId, slurmCmdResult.workerId);
            return true;
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 调用 {@link ExternalTaskService#handleFailure} 一次；成功返回 {@code true}，失败时把异常写入 {@code lastFailure[0]} 并返回 {@code false}。
     */
    private boolean handleFailure(SlurmCmdResult slurmCmdResult) {
        try {
            externalTaskService.handleFailure(slurmCmdResult.taskId, slurmCmdResult.workerId, "Slurm job failed with exit code " + slurmCmdResult.commandExitCode, null, 0, 0L);
            return true;
        } catch( Exception e ) {
            return false;
        }
    }

    private static void sleepBetweenExternalTaskRetries() {
        try {
            Thread.sleep(1000);
        } catch( InterruptedException ex ) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for external task retry", ex);
        }
    }

    /** 未解析到本机 workerId 时不限制；已解析则须与 flag 内 workerId 一致。 */
    private boolean shouldProcessWorkerId(String flagWorkerId) {
        String local = effectiveExternalTaskWorkerId();
        if (local == null) {
            return true;
        }
        return local.equals(flagWorkerId);
    }

    /**
     * 监听 Slurm 工作目录下 {@code *.flag}；仅当 flag 内 workerId 与 {@link #effectiveExternalTaskWorkerId()} 一致
     * （或未配置 worker 过滤）时处理。
     */
    private class SlurmFlagFileHandler extends FileAlterationListenerAdaptor {

        @Override
        public void onFileCreate(File file) {
            try {
                log.info("Slurm flag file created: {}", file.getAbsolutePath());
                String flagBody = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                SlurmCmdResult parsed = parseSlurmFlagFileContent(flagBody);
                if (!shouldProcessWorkerId(parsed.workerId)) {
                    log.debug(
                            "Skip Slurm flag (other worker): file={}, flagWorkerId={}, localWorkerId={}",
                            file.getAbsolutePath(),
                            parsed.workerId,
                            effectiveExternalTaskWorkerId());
                    return;
                }
                String taskId = parsed.taskId;
                String workId = parsed.workerId;
                log.info(
                        "Slurm flag parsed: commandExitCode={}, taskId={}, workerId={}, jobName={}",
                        parsed.commandExitCode,
                        taskId,
                        workId,
                        parsed.jobName);

                int maxAttempts = Math.max(1, slurmProperties.getExternalTaskCompleteMaxAttempts());
                boolean completeSuccess = parsed.commandExitCode == 0;
                Supplier<Boolean> completeHandler = () -> {
                    return completeSuccess ? handleComplete(parsed) : handleFailure(parsed);
                };
                for( int attempt = 1; attempt <= maxAttempts; attempt++ ) {
                    if( completeHandler.get() ) {
                        break;
                    }
                    if( attempt >= maxAttempts ) {
                        log.error(
                                "Failed to report external task failure after {} attempts, taskId={}, workerId={}",
                                maxAttempts,
                                taskId,
                                workId);
                        throw new RuntimeException(
                                "External task failure report failed after " + maxAttempts + " attempts "
                                );
                    }
                    log.warn(
                            "Failed to report external task failure, retrying ({}/{}), taskId: {}, workId: {}",
                            attempt,
                            maxAttempts,
                            taskId,
                            workId);
                    sleepBetweenExternalTaskRetries();
                }
                FileUtils.copyFile(file, new File(file.getAbsolutePath() + ".done"));
                FileUtils.deleteQuietly(file);
            } catch( IOException e ) {
                log.error("Failed to process Slurm flag file: {}", file.getAbsolutePath(), e);
                throw new RuntimeException(e);
            }
        }
    }
}
