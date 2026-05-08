package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.core.retry.JobRetryException;

import java.util.Map;

/**
 * 无特定 {@link SlurmJob#getTaskType()} 解析器、解析器返回 {@code null} 或解析抛错时的默认失败语义：
 * 优先使用错误文件正文（截断），否则使用退出码与作业名拼接的说明。
 */
public class DefaultSlurmExternalTaskFailureResolver implements SlurmExternalTaskFailureResolver {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 8_000;

    /**
     * 占位类型，仅供 Spring 按类型收集 Bean；业务 taskType 不应与之相同。
     */
    static final String TASK_TYPE = "__slurm_default_failure__";

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Exception resolve(
            SlurmJob result, String errorFileContent, Map<String, Object> contextVariables) {
        String fromFile = trimForExceptionMessage(errorFileContent);
        if (fromFile != null && !fromFile.isBlank()) {
            return new JobRetryException(fromFile);
        }
        int exitCode = result != null && result.getExitCode() != null ? result.getExitCode() : 0;
        String jobName = result != null && result.getJobName() != null ? result.getJobName() : "";
        return new JobRetryException(
                "Slurm batch command exited with code "
                        + exitCode
                        + " (jobName="
                        + jobName
                        + ")");
    }

    private String trimForExceptionMessage(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String t = content.trim();
        if (t.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return t.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return t;
    }
}
