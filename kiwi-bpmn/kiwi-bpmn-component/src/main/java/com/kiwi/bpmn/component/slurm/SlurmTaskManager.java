package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.process.ProcessHelper;
import com.kiwi.bpmn.external.ExternalTaskExecution;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Slurm sbatch 提交；作业完成由 {@code sacct} 与 {@link SlurmJobTracker}、Mongo 跟踪驱动。
 */
@Slf4j
public class SlurmTaskManager implements InitializingBean {

    private final SlurmProperties slurmProperties;
    private final SlurmService slurmService;
    private final ObjectProvider<SlurmJobTracker> slurmJobTracker;

    private ThreadPoolTaskExecutor taskExecutor;

    public SlurmTaskManager(
            SlurmProperties slurmProperties,
            SlurmService slurmService,
            ObjectProvider<SlurmJobTracker> slurmJobTracker) {
        this.slurmProperties = slurmProperties;
        this.slurmService = slurmService;
        this.slurmJobTracker = slurmJobTracker;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        int poolSize = slurmProperties.getThreadPoolSize();
        this.taskExecutor = new ThreadPoolTaskExecutor();
        this.taskExecutor.setCorePoolSize(poolSize);
        this.taskExecutor.setMaxPoolSize(poolSize);
        this.taskExecutor.initialize();
        log.info("SlurmTaskManager thread pool initialized: core=max={}", poolSize);
        if (slurmProperties.isFlagListenerEnabled()) {
            log.warn(
                    "kiwi.bpm.slurm.flag-listener-enabled=true is ignored: legacy .flag file watcher has been removed; use sacct (kiwi.bpm.slurm.sacct.enabled).");
        }
        if (slurmProperties.getSacct() != null
                && slurmJobTracker.getIfAvailable() == null) {
            log.warn(
                    "Slurm sacct tracking unavailable (no SlurmJobTracker): "
                            + "configure Mongo / Spring Data MongoDB so SlurmJobRepository is registered for job completion tracking.");
        }
    }

    /**
     * 按 {@code execution} 与 {@code sbatchConfig} 提交 sbatch；{@code taskType} 写入 Mongo 跟踪文档供失败解析等使用。
     */
    public CompletableFuture<SlurmJob> submitSlurmJob(String taskType,
            DelegateExecution execution, SbatchConfig sbatchConfig) {
        File sbatchFile = slurmService.createSbatchFile(execution.getId() + ".sbatch", sbatchConfig);

        log.info(
                "Preparing Slurm submit: processInstanceId={}, activityId={}, executionId={}, jobName={}, sbatchFile={}",
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId(),
                execution.getId(),
                sbatchConfig.getJobName(),
                sbatchFile.getAbsolutePath());

        String externalTaskId = null;
        String workerId = null;
        if (execution instanceof ExternalTaskExecution ext) {
            externalTaskId = ext.getExternalTask().getId();
            workerId = ext.getExternalTask().getWorkerId();
        }
        final String tid = externalTaskId;
        final String wid = workerId;
        return taskExecutor.submitCompletable(() -> {
            SlurmJob job = submitSbatch(sbatchFile);
            job.setJobName(sbatchConfig.getJobName());
            job.setCommand(sbatchConfig.getCommand());
            job.setTaskType(taskType);
            job.setSbatchFilePath(sbatchFile.getAbsolutePath());
            job.setOutputFilePath(sbatchConfig.getOutput_file());
            job.setErrorFilePath(sbatchConfig.getError_file());
            job.setId(job.getJobId());
            job.setExternalTaskId(tid);
            job.setWorkerId(wid);
            Date created = new Date();
            job.setCreatedTime(created);
            // 与 SlurmService#getExternalTaskLockExtensionDurationMs 同源基准（±delta 仅作用于 extendLock）
            long trackMs = slurmService.getSlurmJobMaxDuration(execution);
            job.setExpiration(new Date(created.getTime() + trackMs));
            slurmJobTracker.ifAvailable(t -> t.saveTrackedJob(job));
            return job;
        });
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
            if (exitCode != 0) {
                String errorMessage = new String(drained.stderr(), StandardCharsets.UTF_8);
                log.warn("sbatch failed: exitCode={}, script={}, stderr={}", exitCode, scriptPath, errorMessage);
                throw new RuntimeException("sbatch command failed with exit code " + exitCode + ": " + errorMessage);
            }
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 4
                    || !parts[0].equals("Submitted")
                    || !parts[1].equals("batch")
                    || !parts[2].equals("job")) {
                log.warn("sbatch unexpected stdout: script={}, stdout={}", scriptPath, message.trim());
                throw new RuntimeException("Unexpected sbatch output: " + message);
            }
            String jobId = parts[3];
            log.info("sbatch succeeded: jobId={}, script={}", jobId, scriptPath);
            SlurmJob job = new SlurmJob();
            job.setJobId(jobId);
            job.setId(jobId);
            return job;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("sbatch invocation failed: script={}", scriptPath, e);
            throw new RuntimeException("Failed to submit Slurm job", e);
        }
    }
}
