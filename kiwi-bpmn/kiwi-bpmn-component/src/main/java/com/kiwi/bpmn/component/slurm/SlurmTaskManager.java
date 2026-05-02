package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.process.ProcessHelper;
import com.kiwi.bpmn.external.ExternalTaskExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Slurm sbatch ??? {@link SlurmJobTracker}?{@code sacct}?Mongo ?????
 */
@Slf4j
public class SlurmTaskManager implements InitializingBean {

    private final SlurmProperties slurmProperties;
    private final SlurmService slurmService;
    private final SlurmFlagFileHandler slurmFlagFileHandler;
    private final ObjectProvider<SlurmJobTracker> slurmJobTracker;

    private ThreadPoolTaskExecutor taskExecutor;
    private final FileAlterationMonitor flagFileMonitor = new FileAlterationMonitor(1000);
    private volatile boolean watcherRunning;

    public SlurmTaskManager(
            SlurmProperties slurmProperties,
            SlurmService slurmService,
            SlurmFlagFileHandler slurmFlagFileHandler,
            ObjectProvider<SlurmJobTracker> slurmJobTracker) {
        this.slurmProperties = slurmProperties;
        this.slurmService = slurmService;
        this.slurmFlagFileHandler = slurmFlagFileHandler;
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
                    "kiwi.bpm.slurm.flag-listener-enabled=true: legacy .flag file watcher is enabled; prefer sacct-only (kiwi.bpm.slurm.sacct.enabled).");
        }
        if (slurmProperties.getSacct() != null
                && slurmProperties.getSacct().isEnabled()
                && slurmJobTracker.getIfAvailable() == null) {
            log.warn(
                    "kiwi.bpm.slurm.sacct.enabled=true but Mongo is not available (no SlurmJobTracker): "
                            + "configure spring.data.mongodb and ensure MongoTemplate is present for job completion tracking.");
        }
        String effective = slurmFlagFileHandler.effectiveExternalTaskWorkerId();
        if (effective != null) {
            log.info(
                    "Slurm sacct tracking will filter by workerId: {} (from kiwi.bpm.external-task.worker-id)",
                    effective);
        } else {
            log.info(
                    "Slurm worker filter off for sacct queries: set kiwi.bpm.external-task.worker-id to restrict tracking on shared DB");
        }
    }

    /**
     * @deprecated ??? .flag???? {@code sacct} ? {@link SlurmJobTracker}??? {@link SlurmProperties#isFlagListenerEnabled()} ? true ??????
     */
    @Deprecated
    public synchronized void startFlagWatcher() {
        try {
            if (watcherRunning) {
                log.debug("Slurm flag watcher already running, skip start");
                return;
            }
            flagFileMonitor.start();
            File dir = slurmService.getShellFileDir();
            FileAlterationObserver observer =
                    FileAlterationObserver.builder().setFile(dir).setFileFilter(new SuffixFileFilter("flag")).get();
            observer.addListener(slurmFlagFileHandler);
            flagFileMonitor.addObserver(observer);
            this.watcherRunning = true;
            log.info("Slurm flag watcher started on directory: {}", dir.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to start Slurm flag watcher", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * ?? {@code execution} ? {@code sbatchConfig} ?? sbatch ????????? sacct ??? Mongo ?? {@link SlurmJob} ?????
     */
    public CompletableFuture<SlurmJob> submitSlurmJob(DelegateExecution execution, SbatchConfig sbatchConfig) {
        File sbatchFile = slurmService.createSbatchFile(execution.getId() + ".sbatch", sbatchConfig);

        log.info(
                "Preparing Slurm submit: processInstanceId={}, activityId={}, executionId={}, jobName={}, sbatchFile={}",
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId(),
                execution.getId(),
                sbatchConfig.getJobName(),
                sbatchFile.getAbsolutePath());

        if (slurmProperties.isFlagListenerEnabled()) {
            startFlagWatcher();
        }

        String externalTaskId = null;
        String workerId = null;
        String jobNameForTrack = null;
        if (execution instanceof ExternalTaskExecution ext) {
            externalTaskId = ext.getExternalTask().getId();
            workerId = ext.getExternalTask().getWorkerId();
            jobNameForTrack = sbatchConfig.getJobName();
        }
        final String tid = externalTaskId;
        final String wid = workerId;
        final String jn = jobNameForTrack;
        return taskExecutor.submitCompletable(() -> {
            SlurmJob job = submitSbatch(sbatchFile);
            job.setJobName(sbatchConfig.getJobName());
            job.setSbatchFilePath(sbatchFile.getAbsolutePath());
            job.setOutputFilePath(sbatchConfig.getOutput_file());
            job.setErrorFilePath(sbatchConfig.getError_file());
            job.setId(job.getJobId());
            if (tid != null && wid != null && jn != null) {
                String ew = slurmFlagFileHandler.effectiveExternalTaskWorkerId();
                if (ew == null || ew.equals(wid)) {
                    job.setExternalTaskId(tid);
                    job.setWorkerId(wid);
                    slurmJobTracker.ifAvailable(t -> t.saveTrackedJob(job));
                }
            }
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
