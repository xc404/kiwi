package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.core.retry.JobRetryException;
import com.kiwi.bpmn.core.retry.RetryPlan;
import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import com.kiwi.bpmn.external.utils.DtoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.externaltask.ExternalTask;
import org.springframework.beans.factory.ObjectProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 根据 {@link SlurmJob} 与退出码向 Camunda 上报外部任务 {@code complete} 或 {@code handleFailure}。
 * 与 {@link SlurmExternalTaskFailureResolver} 的桥接在类内通过临时 {@link SlurmResult} 完成；错误文件路径解析后读入文本一并传入。
 */
@Slf4j
public class SlurmJobCompleteProcessor {

    /** 与 {@link SlurmExternalTaskHandler} 写入的流程变量名一致；{@link SlurmJob#getErrorFilePath()} 优先。 */
    public static final String ERROR_FILE_PATH_VARIABLE = "errorFilePath";

    private final ProcessEngine processEngine;
    private final ExternalTaskService externalTaskService;
    private final ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner;
    private final List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers;
    private final SlurmProperties slurmProperties;

    public SlurmJobCompleteProcessor(
            ProcessEngine processEngine,
            ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner,
            List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers,
            SlurmProperties slurmProperties) {
        this.processEngine = processEngine;
        this.externalTaskService = processEngine.getExternalTaskService();
        this.externalTaskRetryPlanner = externalTaskRetryPlanner;
        this.slurmExternalTaskFailureResolvers =
                slurmExternalTaskFailureResolvers != null ? slurmExternalTaskFailureResolvers : List.of();
        this.slurmProperties = slurmProperties;
    }

    /**
     * {@code exitCode == 0} 时 complete，否则 handleFailure；含上报重试。
     *
     * @return 是否已成功提交 Camunda 终态
     */
    public boolean processTerminal(SlurmJob job, int exitCode) {
        if (!hasExternalTaskKeys(job)) {
            log.warn("processTerminal skipped: missing externalTaskId or workerId on SlurmJob");
            return false;
        }
        String taskId = job.getExternalTaskId();
        String workerId = job.getWorkerId();
        int maxAttempts = externalTaskCompleteMaxAttempts();
        Throwable[] lastFailure = new Throwable[1];
        boolean success = exitCode == 0;
        final Exception failureException;
        final RetryPlan retryPlan;
        if (success) {
            failureException = null;
            retryPlan = new RetryPlan(0, 0);
        } else {
            failureException = resolveFailureException(job, exitCode);
            retryPlan = resolveRetryPlan(job, failureException).orElseGet(() -> new RetryPlan(0, 0));
        }
        Supplier<Boolean> step =
                () -> {
                    if (success) {
                        return handleComplete(job, exitCode);
                    }
                    Optional<Throwable> err = reportHandleFailure(job, exitCode, failureException, retryPlan);
                    if (err.isEmpty()) {
                        return true;
                    }
                    lastFailure[0] = err.get();
                    return false;
                };
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (step.get()) {
                return true;
            }
            if (attempt >= maxAttempts) {
                log.error(
                        "Failed to report external task failure after {} attempts, taskId={}, workerId={}, cause={}",
                        maxAttempts,
                        taskId,
                        workerId,
                        lastFailure[0] != null ? lastFailure[0].getMessage() : "");
                return false;
            }
            log.warn(
                    "Failed to report external task failure, retrying ({}/{}), taskId={}, workerId={}",
                    attempt,
                    maxAttempts,
                    taskId,
                    workerId);
            sleepBetweenExternalTaskRetries();
        }
        return false;
    }

    /**
     * 单次 {@code complete}。{@code exitCode} 仅用于日志。
     */
    public boolean handleComplete(SlurmJob job, int exitCode) {
        if (!hasExternalTaskKeys(job)) {
            log.warn("handleComplete skipped: missing externalTaskId or workerId");
            return false;
        }
        try {
            externalTaskService.complete(
                    job.getExternalTaskId(), job.getWorkerId(), Collections.emptyMap(), Collections.emptyMap());
            log.debug(
                    "externalTask complete: taskId={}, workerId={}, exitCode={}",
                    job.getExternalTaskId(),
                    job.getWorkerId(),
                    exitCode);
            return true;
        } catch (Exception e) {
            log.warn(
                    "complete failed: taskId={}, workerId={}, exitCode={}, error={}",
                    job.getExternalTaskId(),
                    job.getWorkerId(),
                    exitCode,
                    e.getMessage());
            return false;
        }
    }

    /**
     * 单次失败上报（自定义失败解析 + 重试计划 + {@code handleFailure}）。
     *
     * @return empty 表示引擎已接受；否则为 {@code handleFailure} 抛出的错误
     */
    public Optional<Throwable> handleFailure(SlurmJob job, int exitCode) {
        if (!hasExternalTaskKeys(job)) {
            return Optional.of(new IllegalStateException("SlurmJob missing externalTaskId or workerId"));
        }
        Exception failure = resolveFailureException(job, exitCode);
        RetryPlan plan = resolveRetryPlan(job, failure).orElseGet(() -> new RetryPlan(0, 0));
        return reportHandleFailure(job, exitCode, failure, plan);
    }

    private Optional<Throwable> reportHandleFailure(
            SlurmJob job, int exitCode, Exception failure, RetryPlan retryPlan) {
        String errMsg = failure.getMessage();
        if (errMsg == null || errMsg.isBlank()) {
            errMsg = defaultFailureMessage(job, exitCode);
        }
        String details = buildFailureDetails(job, exitCode, failure);
        int retries = retryPlan.nextRetries();
        long retryTimeoutMs = retryPlan.retryTimeoutMs();
        try {
            externalTaskService.handleFailure(
                    job.getExternalTaskId(),
                    job.getWorkerId(),
                    errMsg,
                    details,
                    retries,
                    retryTimeoutMs,
                    Collections.emptyMap(),
                    Collections.emptyMap());
            return Optional.empty();
        } catch (Exception e) {
            log.warn(
                    "handleFailure failed: taskId={}, workerId={}, retries={}, error={}",
                    job.getExternalTaskId(),
                    job.getWorkerId(),
                    retries,
                    e.getMessage());
            return Optional.of(e);
        }
    }

    private Exception resolveFailureException(SlurmJob job, int exitCode) {
        SlurmResult asResult = toSlurmResult(job, exitCode);
        ExternalTask engineTask =
                externalTaskService.createExternalTaskQuery().externalTaskId(job.getExternalTaskId()).singleResult();
        String errorFilePath = job.getErrorFilePath();
        String errorFileContent = readErrorFileContent(errorFilePath);
        Map<String, Object> contextVariables;
        if (engineTask == null) {
            return defaultFailureException(asResult, errorFileContent);
        }
        RuntimeService runtimeService = processEngine.getRuntimeService();
        contextVariables = safeExecutionVariables(runtimeService, engineTask.getExecutionId());

        SlurmExternalTaskFailureResolver handler = handlerForTopic(engineTask.getTopicName());
        if (handler != null) {
            try {
                Exception custom = handler.resolve(asResult, errorFileContent, contextVariables);
                return custom != null ? custom : defaultFailureException(asResult, errorFileContent);
            } catch (Exception ex) {
                log.warn("SlurmExternalTaskFailureResolver failed, using default: {}", ex.toString());
                return defaultFailureException(asResult, errorFileContent);
            }
        }
        return defaultFailureException(asResult, errorFileContent);
    }

    private Optional<RetryPlan> resolveRetryPlan(SlurmJob job, Exception failure) {
        ExternalTask engineTask =
                externalTaskService.createExternalTaskQuery().externalTaskId(job.getExternalTaskId()).singleResult();
        if (engineTask == null) {
            return Optional.empty();
        }
        ExternalTaskRetryPlanner planner = externalTaskRetryPlanner.getIfAvailable();
        if (planner == null) {
            return Optional.empty();
        }
        try {
            ExternalTaskImpl clientTask = DtoUtils.fromEngineExternalTask(engineTask);
            if (clientTask == null) {
                return Optional.empty();
            }
            return Optional.of(planner.plan(clientTask, failure));
        } catch (Exception ex) {
            log.warn("ExternalTaskRetryPlanner.plan failed, using retries=0: {}", ex.toString());
            return Optional.empty();
        }
    }

    private static SlurmResult toSlurmResult(SlurmJob job, int exitCode) {
        String jn = job.getJobName() != null ? job.getJobName() : "";
        return new SlurmResult(exitCode, job.getExternalTaskId(), job.getWorkerId(), jn);
    }

    private static boolean hasExternalTaskKeys(SlurmJob job) {
        return job != null && notBlank(job.getExternalTaskId()) && notBlank(job.getWorkerId());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String effectiveErrorFilePath(SlurmJob job, RuntimeService runtimeService, String executionId) {
        if (job != null && notBlank(job.getErrorFilePath())) {
            return job.getErrorFilePath().trim();
        }
        return readStringExecutionVariable(runtimeService, executionId, ERROR_FILE_PATH_VARIABLE);
    }

    private static Map<String, Object> safeExecutionVariables(RuntimeService runtimeService, String executionId) {
        try {
            Map<String, Object> m = runtimeService.getVariables(executionId);
            return m != null ? m : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int externalTaskCompleteMaxAttempts() {
        return Math.max(1, slurmProperties.getExternalTaskCompleteMaxAttempts());
    }

    private SlurmExternalTaskFailureResolver handlerForTopic(String topicName) {
        if (topicName == null) {
            return null;
        }
        for (SlurmExternalTaskFailureResolver h : slurmExternalTaskFailureResolvers) {
            if (topicName.equals(h.topic())) {
                return h;
            }
        }
        return null;
    }

    private static String readStringExecutionVariable(RuntimeService runtimeService, String executionId, String name) {
        if (runtimeService == null || executionId == null || name == null) {
            return null;
        }
        try {
            Object v = runtimeService.getVariable(executionId, name);
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 若 {@code errorFileContent} 非空则以其作为 {@link JobRetryException} 的 message（过长会截断）；否则使用退出码与作业名拼接的默认文案。
     */
    private static JobRetryException defaultFailureException(SlurmResult parsed, String errorFileContent) {
        String fromFile = trimForExceptionMessage(errorFileContent);
        if (fromFile != null && !fromFile.isBlank()) {
            return new JobRetryException(fromFile);
        }
        return new JobRetryException(
                "Slurm batch command exited with code "
                        + parsed.getCommandExitCode()
                        + " (jobName="
                        + parsed.getJobName()
                        + ")");
    }

    private String readErrorFileContent(String errorFilePath) {
        if (errorFilePath == null || errorFilePath.isBlank()) {
            return null;
        }
        try {
            String text = FileUtils.readFileToString(new File(errorFilePath.trim()), StandardCharsets.UTF_8);
            final int maxRead = 128_000;
            if (text.length() > maxRead) {
                return text.substring(0, maxRead) + "\n...(truncated)";
            }
            return text;
        } catch (IOException e) {
            log.debug("Could not read Slurm error file: {}", errorFilePath, e);
            return null;
        }
    }

    private static String trimForExceptionMessage(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String t = content.trim();
        final int max = 8_000;
        if (t.length() > max) {
            return t.substring(0, max) + "...";
        }
        return t;
    }

    private static String defaultFailureMessage(SlurmJob job, int exitCode) {
        String jn = job != null && job.getJobName() != null ? job.getJobName() : "";
        return "Slurm batch command exited with code " + exitCode + " (jobName=" + jn + ")";
    }

    private static String buildFailureDetails(SlurmJob job, int exitCode, Exception failure) {
        StringBuilder sb = new StringBuilder();
        sb.append("slurmJob: jobId=")
                .append(job != null ? job.getJobId() : "")
                .append(", externalTaskId=")
                .append(job != null ? job.getExternalTaskId() : "")
                .append(", exitCode=")
                .append(exitCode)
                .append('\n');
        StringWriter sw = new StringWriter();
        failure.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        if (stack.length() > 8000) {
            stack = stack.substring(0, 8000) + "...";
        }
        sb.append(stack);
        return sb.toString();
    }

    private static void sleepBetweenExternalTaskRetries() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for external task retry", ex);
        }
    }
}
