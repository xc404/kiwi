package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.external.ExternalTaskExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.commons.utils.IoUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Slurm 任务提交（sbatch）与作业目录下 .flag 文件监听（驱动 External Task 完成）。
 */
@Slf4j
public class SlurmTaskManager implements InitializingBean {

    private final SlurmProperties slurmProperties;
    private final SlurmService slurmService;
    private final ProcessEngine processEngine;

    private ThreadPoolTaskExecutor taskExecutor;
    private final FileAlterationMonitor flagFileMonitor = new FileAlterationMonitor(1000);
    private volatile boolean watcherRunning;

    public SlurmTaskManager(SlurmProperties slurmProperties, SlurmService slurmService, ProcessEngine processEngine) {
        this.slurmProperties = slurmProperties;
        this.slurmService = slurmService;
        this.processEngine = processEngine;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.taskExecutor = new ThreadPoolTaskExecutor();
        this.taskExecutor.setCorePoolSize(slurmProperties.getThreadPoolSize());
        this.taskExecutor.setMaxPoolSize(slurmProperties.getThreadPoolSize());
        this.taskExecutor.initialize();
    }

    /**
     * 启动对 {@link SlurmService#getShellFileDir()} 下 <code>*.flag</code> 的监听（幂等）。
     */
    public synchronized void startFlagWatcher() {
        try {
            if (watcherRunning) {
                return;
            }
            flagFileMonitor.start();
            File dir = slurmService.getShellFileDir();
            FileAlterationObserver observer = FileAlterationObserver.builder()
                    .setFile(dir)
                    .setFileFilter(new SuffixFileFilter("flag"))
                    .get();
            observer.addListener(new FlagFileCreatedListener());
            flagFileMonitor.addObserver(observer);
            this.watcherRunning = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据 {@code execution} 与 {@code sbatchConfig} 生成 sbatch 文件、写入流程变量、启动 flag 监听并提交 sbatch。
     * 若为 {@link ExternalTaskExecution}，则在脚本末尾追加写入 {@code $SLURM_JOB_ID.flag} 的 echo 行。
     * <p>
     * 返回的 {@link SlurmJob} 含作业 ID、作业名、脚本与日志路径；提交成功后会把完整结果写回 {@code execution}。
     */
    public CompletableFuture<SlurmJob> submitSlurmJob(DelegateExecution execution, SbatchConfig sbatchConfig) {
        String command = ExecutionUtils.getStringInputVariable(execution, "command").orElseThrow();
        File sbatchFile = slurmService.createSbatchFile(execution.getId() + ".sbatch", sbatchConfig, command);
//        writeSlurmPathsBeforeSubmit(execution, sbatchFile, sbatchConfig);

        startFlagWatcher();

        String externalTaskId = null;
        String workerId = null;
        String jobNameForFlag = null;
        if (execution instanceof ExternalTaskExecution ext) {
            externalTaskId = ext.getExternalTask().getId();
            workerId = ext.getExternalTask().getWorkerId();
            jobNameForFlag = sbatchConfig.getJobName();
        }
        final String tid = externalTaskId;
        final String wid = workerId;
        final String jn = jobNameForFlag;
        if (tid != null && wid != null && jn != null) {
            appendFlagCompletionToSbatch(sbatchFile, tid, wid, jn);
        }
        return taskExecutor.submitCompletable(() -> {
            SlurmJob job = submitSbatch(sbatchFile);
            job.setJobName(sbatchConfig.getJobName());
            job.setSbatchFilePath(sbatchFile.getAbsolutePath());
            job.setOutputFilePath(sbatchConfig.getOutput_file());
            job.setErrorFilePath(sbatchConfig.getError_file());
//            applySlurmJobToExecution(execution, job);
            return job;
        });
    }

//    /**
//     * 提交 sbatch 前即可确定的变量（脚本路径、日志路径、作业名）。
//     */
//    private static void writeSlurmPathsBeforeSubmit(DelegateExecution execution, File sbatchFile, SbatchConfig sbatchConfig) {
//        execution.setVariable("sbatchFilePath", sbatchFile.getAbsolutePath());
//        execution.setVariable("outputFilePath", sbatchConfig.getOutput_file());
//        execution.setVariable("errorFilePath", sbatchConfig.getError_file());
//        execution.setVariable("slurmJobName", sbatchConfig.getJobName());
//    }
//
//    /**
//     * 提交成功后写入 Slurm 作业 ID，并同步 {@link SlurmJob} 全量字段到流程变量。
//     */
//    private static void applySlurmJobToExecution(DelegateExecution execution, SlurmJob job) {
//        execution.setVariable("slurmJobId", job.getJobId());
//        execution.setVariable("slurmJobName", job.getJobName());
//        execution.setVariable("sbatchFilePath", job.getSbatchFilePath());
//        execution.setVariable("outputFilePath", job.getOutputFilePath());
//        execution.setVariable("errorFilePath", job.getErrorFilePath());
//    }

    private void appendFlagCompletionToSbatch(File sbatchFile, String taskId, String workerId, String jobName) {
        try {
            String line = "\n\n" + buildFlagCompletionShellLine(taskId, workerId, jobName);
            FileUtils.writeStringToFile(sbatchFile, line, StandardCharsets.UTF_8, true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append flag completion to sbatch file", e);
        }
    }

    private String buildFlagCompletionShellLine(String taskId, String workerId, String jobName) {
        String content = taskId + "," + workerId + "," + jobName;
        return "echo " + content + " > " + slurmService.getShellFileDir().getAbsolutePath() + "/$SLURM_JOB_ID.flag";
    }

    private SlurmJob submitSbatch(File batchFile) {
        batchFile.setExecutable(true);
        ProcessBuilder processBuilder = new ProcessBuilder("sbatch", batchFile.getAbsolutePath());
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            String message = IoUtil.inputStreamAsString(process.getInputStream());
            if (exitCode != 0) {
                String errorMessage = IoUtil.inputStreamAsString(process.getErrorStream());
                throw new RuntimeException("sbatch command failed with exit code " + exitCode + ": " + errorMessage);
            }
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 4 || !parts[0].equals("Submitted") || !parts[1].equals("batch") || !parts[2].equals("job")) {
                throw new RuntimeException("Unexpected sbatch output: " + message);
            }
            String jobId = parts[3];
            SlurmJob job = new SlurmJob();
            job.setJobId(jobId);
            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit Slurm job", e);
        }
    }

    private class FlagFileCreatedListener extends FileAlterationListenerAdaptor {

        @Override
        public void onFileCreate(File file) {
            try {
                String taskIdAndWorkId = FileUtils.readFileToString(file);
                String[] split = taskIdAndWorkId.split(",");
                String taskId = split[0];
                String workId = split[1];
                log.info("Complete external task, taskId: {}, workId: {}", taskId, workId);
                while (true) {
                    try {
                        processEngine.getExternalTaskService().complete(taskId, workId);
                        break;
                    } catch (Exception e) {
                        log.warn("Failed to complete external task, retrying... taskId: {}, workId: {}, error: {}",
                                taskId, workId, e.getMessage());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                FileUtils.copyFile(file, new File(file.getAbsolutePath() + ".done"));
                FileUtils.deleteQuietly(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
