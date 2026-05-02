package com.kiwi.bpmn.component.slurm;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Slurm {@code *.flag} 单行解析结果：退出码、外部任务 id、workerId、作业名。
 */
public final class SlurmResult
{

    private static final Pattern SIGNED_INT = Pattern.compile("-?\\d+");

    private final int commandExitCode;
    private final String taskId;
    private final String workerId;
    private final String jobName;

    public SlurmResult(int commandExitCode, String taskId, String workerId, String jobName) {
        this.commandExitCode = commandExitCode;
        this.taskId = taskId;
        this.workerId = workerId;
        this.jobName = jobName;
    }

    public int getCommandExitCode() {
        return commandExitCode;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getJobName() {
        return jobName;
    }

    /**
     * 新格式：{@code 退出码,taskId,workerId,jobName}；
     * 兼容历史上曾写入更多逗号分列的内容（仅取前四列）；
     * 兼容旧格式 {@code taskId,workerId,jobName}（退出码视为 0）。
     */
    public static SlurmResult parse(String raw) throws IOException {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new IOException("Slurm flag file is empty");
        }
        String[] parts = text.split(",", -1);
        if (parts.length >= 4 && SIGNED_INT.matcher(parts[0]).matches()) {
            try {
                int ec = Integer.parseInt(parts[0]);
                return new SlurmResult(ec, parts[1].trim(), parts[2].trim(), parts[3].trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid exit code in flag file: " + text, e);
            }
        }
        if (parts.length >= 3) {
            return new SlurmResult(0, parts[0].trim(), parts[1].trim(), parts[2].trim());
        }
        throw new IOException("Invalid Slurm flag content (expected 3 or 4+ comma-separated fields): " + text);
    }
}
