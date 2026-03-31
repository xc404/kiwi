package com.kiwi.bpmn.component.slurm;

import org.apache.commons.io.FileUtils;
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

    public File createSbatchFile(String fileName, SbatchConfig sbatchConfig, String cmd)  {
        File dir = shellFileDir;
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File sbatchFile = new File(dir, fileName);
        try {
            FileUtils.writeStringToFile(sbatchFile, "#!/bin/bash\n\n", "UTF-8");
            FileUtils.writeStringToFile(sbatchFile, sbatchConfig.toSbatchCmd() + "\n\n", "UTF-8", true);
            FileUtils.writeStringToFile(sbatchFile, cmd + "\n\n", "UTF-8", true);
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        return sbatchFile;
    }

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
