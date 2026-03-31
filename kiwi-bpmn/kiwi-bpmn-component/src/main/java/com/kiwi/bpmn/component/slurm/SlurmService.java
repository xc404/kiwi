package com.kiwi.bpmn.component.slurm;

import org.apache.commons.io.FileUtils;
import org.camunda.bpm.engine.ExternalTaskService;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.io.IOException;

public class SlurmService implements InitializingBean
{
    protected final SlurmProperties slurmProperties;
    private File shellFileDir;

    public SlurmService(SlurmProperties slurmProperties) {
        this.slurmProperties = slurmProperties;
    }

    public File getShellFileDir() {
        return shellFileDir;
    }


    public File createSbatchFile(String fileName, SbatchConfig sbatchConfig, String cmd) throws IOException {
        File file = shellFileDir;
        if( !file.exists() ) {
            file.mkdirs();
        }
        File sbatchFile = new File(file, fileName);
        FileUtils.writeStringToFile(sbatchFile, "#!/bin/bash\n\n", "UTF-8");
        FileUtils.writeStringToFile(sbatchFile, sbatchConfig.toSbatchCmd() + "\n\n", "UTF-8", true);
        FileUtils.writeStringToFile(sbatchFile, cmd + "\n\n", "UTF-8", true);
//        appendCompleteCmd(sbatchFile, sbatchConfig.getJobName());
        return sbatchFile;
    }

//    private void appendCompleteCmd(File sbatchFile, String jobName) throws IOException {
//        String completeCmd = "echo " + jobName + " > " + shellFileDir.getAbsolutePath() + "/%SLURM_JOB_ID%.flag";
//        FileUtils.writeStringToFile(sbatchFile, completeCmd + "\n", "UTF-8", true);
//    }

    public String getFlagFilePath(String jobId) {
        return shellFileDir.getAbsolutePath() + "/" + jobId + ".flag";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.shellFileDir = new File(slurmProperties.getSlurmFilePath());
        if( !this.shellFileDir.exists() ) {
            this.shellFileDir.mkdirs();
        }
        ExternalTaskService externalTaskService;
    }
}
