package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.core.retry.JobRetryException;

import java.util.Map;

/**
 * 默认 Slurm 外部任务失败解析：把 {@link SlurmJob#getErrorMessage()} 与 stderr 正文合成一条可读消息，
 * 再包装为 {@link JobRetryException}；若无可用细节则回落到退出码与作业名。
 * <p>
 * 最终 {@link JobRetryException#getMessage()} 首行会附带 {@code [slurmState=…]}（当 {@link SlurmJob#getSlurmState()} 非空时），
 * 便于区分 sacct 终态（如 {@code FAILED}、{@code TIMEOUT}）与本系统扩展状态。
 */
public class DefaultSlurmExternalTaskFailureResolver implements SlurmExternalTaskFailureResolver {

    private static final int MAX_MESSAGE_CHARS = 8_000;

    private static final String STDERR_SEPARATOR = "\n\n--- stderr ---\n\n";

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
        String jobSnippet = trimToOptional(result != null ? result.getErrorMessage() : null);
        String fileSnippet = trimToOptional(errorFileContent);

        String detail = mergeDetail(jobSnippet, fileSnippet);
        if (detail == null || detail.isBlank()) {
            int exitCode = result != null && result.getExitCode() != null ? result.getExitCode() : 0;
            String jobName = result != null && result.getJobName() != null ? result.getJobName() : "";
            detail =
                    "Slurm batch command exited with code "
                            + exitCode
                            + " (jobName="
                            + jobName
                            + ")";
        }

        String message = prependSlurmStateLine(result, detail);
        return new JobRetryException(clamp(message));
    }

    /**
     * 在正文前增加一行 {@code [slurmState=COMPLETED]} 形式标记（sacct 状态或本系统常量如 {@link SlurmJobResult#STATE_TRACKING_EXPIRED}）。
     */
    private String prependSlurmStateLine(SlurmJob job, String body) {
        String st = job != null ? trimToOptional(job.getSlurmState()) : null;
        if (st == null) {
            return body;
        }
        return "[slurmState=" + st + "]\n" + body;
    }

    /**
     * 合并 Job 侧文案与 stderr：仅一侧有则返回该侧；全文相同则去重；两侧都有且不同则分段拼接；最后整体截断。
     */
    private String mergeDetail(String jobSnippet, String fileSnippet) {
        boolean noJob = jobSnippet == null || jobSnippet.isEmpty();
        boolean noFile = fileSnippet == null || fileSnippet.isEmpty();
        if (noJob && noFile) {
            return null;
        }
        if (noJob) {
            return clamp(fileSnippet);
        }
        if (noFile) {
            return clamp(jobSnippet);
        }
        if (jobSnippet.equals(fileSnippet)) {
            return clamp(jobSnippet);
        }
        return clamp(jobSnippet + STDERR_SEPARATOR + fileSnippet);
    }

    private String trimToOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private String clamp(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.length() <= MAX_MESSAGE_CHARS) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
