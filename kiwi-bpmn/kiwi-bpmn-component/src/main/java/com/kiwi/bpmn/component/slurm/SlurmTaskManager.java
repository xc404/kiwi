package com.kiwi.bpmn.component.slurm;

import org.camunda.commons.utils.IoUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class SlurmTaskManager implements InitializingBean
{
    @Autowired
    private SlurmProperties slurmProperties;
    private ThreadPoolTaskExecutor taskExecutor;


    @Override
    public void afterPropertiesSet() throws Exception {
        this.taskExecutor = new ThreadPoolTaskExecutor();
        this.taskExecutor.setCorePoolSize(slurmProperties.getThreadPoolSize());
        this.taskExecutor.setMaxPoolSize(slurmProperties.getThreadPoolSize());
        this.taskExecutor.initialize();
    }

    public CompletableFuture<SlurmJob> submitSlurmJob(File sbatchFile) {

        return taskExecutor.submitCompletable(() -> {
            return slurm(sbatchFile);
        });
    }

    private SlurmJob slurm(File batchFile){
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
            return new SlurmJob(jobId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit Slurm job", e);
        }
    }
}
