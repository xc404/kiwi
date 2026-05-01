package com.kiwi.bpmn.component.slurm;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
            // 子 shell 内执行用户命令并捕获退出码，供后续 flag 行写入（见 SlurmTaskManager 追加的 printf）
            FileUtils.writeStringToFile(sbatchFile, "set +e\n(\n", StandardCharsets.UTF_8, true);
            FileUtils.writeStringToFile(sbatchFile, cmd, StandardCharsets.UTF_8, true);
            FileUtils.writeStringToFile(
                    sbatchFile,
                    "\n)\n__KIWI_SLURM_CMD_EC=$?\nset -e\n\n",
                    StandardCharsets.UTF_8,
                    true);
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
