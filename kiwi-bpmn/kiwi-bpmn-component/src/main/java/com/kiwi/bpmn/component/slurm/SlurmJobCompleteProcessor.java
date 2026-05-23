package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.core.retry.RetryPlan;
import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import com.kiwi.bpmn.external.utils.DtoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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
 * 根据 sacct 解析得到的 {@link SlurmJob} 快照向 Camunda 上报外部任务 {@code complete} 或 {@code handleFailure}。
 * 对外入口为 {@link #complete(SlurmJob, SlurmJobResult)}；与 {@link SlurmExternalTaskFailureResolver} 的桥接在类内完成（按 {@link SlurmJob#getTaskType()} 匹配）；
 * 错误文件路径解析后读入文本一并传入。
 */
@Slf4j
public class SlurmJobCompleteProcessor
{

    private static final int MAX_ERROR_MESSAGE_LENGTH = 8_000;
    private static final int MAX_ERROR_FILE_READ_LENGTH = 128_000;
    private static final int RETRY_INTERVAL_MS = 1_000;

    private final ProcessEngine processEngine;
    private final ExternalTaskService externalTaskService;
    private final ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner;
    private final List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers;
    private final DefaultSlurmExternalTaskFailureResolver defaultFailureResolver;
    private final SlurmProperties slurmProperties;
    private final SlurmService slurmService;
    private final SlurmJobRepository slurmJobRepository;
    private final RuntimeService runtimeService;

    public SlurmJobCompleteProcessor(
            ProcessEngine processEngine,
            ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner,
            List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers,
            DefaultSlurmExternalTaskFailureResolver defaultFailureResolver,
            SlurmProperties slurmProperties,
            SlurmService slurmService,
            SlurmJobRepository slurmJobRepository) {
        this.processEngine = processEngine;
        this.externalTaskService = processEngine.getExternalTaskService();
        this.externalTaskRetryPlanner = externalTaskRetryPlanner;
        this.slurmExternalTaskFailureResolvers =
                slurmExternalTaskFailureResolvers != null ? slurmExternalTaskFailureResolvers : List.of();
        this.defaultFailureResolver = defaultFailureResolver;
        this.slurmProperties = slurmProperties;
        this.slurmService = slurmService;
        this.slurmJobRepository = slurmJobRepository;
        this.runtimeService = processEngine.getRuntimeService();
    }

    /**
     * 对外唯一入口：根据 sacct 侧解析得到的轻量快照完成 Camunda 终态上报。
     * <p>
     * {@code exitCode == 0} 时 complete，否则 handleFailure；含上报重试。快照须含 {@link SlurmJob#getExternalTaskId()}、
     * {@link SlurmJob#getJobId()}（与 Mongo 跟踪文档主键及 sacct 一致）、{@link SlurmJobResult#getExitCode()}（及可选 worker/jobName）；
     * Mongo 乐观锁保证多节点 / 多入口下不会重复上报。
     *
     * @return 是否已成功提交 Camunda 终态（含幂等跳过视为成功）
     */
    public void complete(SlurmJob slurmJob, SlurmJobResult slurmJobResult) {
        slurmJob.setSlurmState(slurmJobResult.getSlurmState());
        slurmJob.setErrorMessage(slurmJobResult.getErrorMessage());
        slurmJob.setExitCode(slurmJobResult.getExitCode());
        if( StringUtils.isBlank(slurmJob.getExternalTaskId()) ) {
            this.setJobCompleteStatus(slurmJob, slurmJobResult);
            return;
        }
        boolean updated = withOptimisticLock(
                slurmJob, () -> completeExternalTaskWithRetries(slurmJob, slurmJobResult));
        if( updated ) {
            this.setJobCompleteStatus(slurmJob, slurmJobResult);
        }
    }


    /**
     * Camunda 已接受终态后，将 sacct/失败解析得到的 {@link SlurmJobResult} 写回 Mongo 中的 {@link SlurmJob}
     *（退出码、说明、sacct 状态）。库中无对应文档时 no-op。
     */
    private void setJobCompleteStatus(SlurmJob job, SlurmJobResult slurmJobResult) {
        SlurmJobRepository repo = slurmJobRepository;
        Optional<SlurmJob> opt = repo.findById(job.getJobId());
        if( opt.isEmpty() ) {
            return;
        }
        SlurmJob persisted = opt.get();
        if( slurmJobResult.getExitCode() != null ) {
            persisted.setExitCode(slurmJobResult.getExitCode());
        }
        persisted.setErrorMessage(slurmJobResult.getErrorMessage());
        if( slurmJobResult.getSlurmState() != null ) {
            persisted.setSlurmState(slurmJobResult.getSlurmState());
        }
        persisted.setStatus(SlurmJobStatus.Completed);
        repo.save(persisted);
    }

    /**
     * 模板方法：解析 Mongo 完成流程乐观锁后执行 {@code camundaReport}；仅在 Camunda 接受上报且本线程持有锁时
     * {@link SlurmJobRepository#finalizeAfterCamundaReport}，否则在失败路径上 {@link SlurmJobRepository#releaseCompleteProcessLock}。
     */
    private boolean withOptimisticLock(
            SlurmJob job, Supplier<ReportOutcome> camundaReport) {
        String jobId = job.getJobId();
        LockOutcome outcome = acquireLock(jobId);
        if( outcome == LockOutcome.ALREADY_COMPLETED ) {
            return true;
        }
        if( outcome == LockOutcome.LOST_RACE ) {
            log.debug(
                    "complete skipped: could not acquire SlurmJob complete-process lock, jobId={}", jobId);
            return false;
        }
        boolean lockHeld = outcome == LockOutcome.ACQUIRED;
        boolean camundaOk = false;
        try {
            ReportOutcome reportOutcome = camundaReport.get();
            camundaOk = reportOutcome.accepted();
            if( camundaOk && lockHeld ) {
                slurmJobRepository.finalizeAfterCamundaReport(jobId);
            }
            return camundaOk;
        } finally {
            if( lockHeld && !camundaOk ) {
                try {
                    slurmJobRepository.releaseCompleteProcessLock(jobId);
                } catch( Exception ex ) {
                    log.warn("releaseCompleteProcessLock failed for jobId={}: {}", jobId, ex.toString());
                }
            }
        }
    }

    /** Camunda 终态上报一次尝试的结果（供 Mongo finalize 持久化 exit / 文案） */
    private record ReportOutcome(boolean accepted, String errorMessageForPersist)
    {
    }

    /**
     * 向 Camunda 上报终态（complete / handleFailure），含失败重试；不含 Mongo 锁的获取与释放。
     */
    private ReportOutcome completeExternalTaskWithRetries(SlurmJob job, SlurmJobResult slurmJobResult) {
        int exitCode = Optional.ofNullable(slurmJobResult).map(SlurmJobResult::getExitCode).orElse(0);
        String taskId = job.getExternalTaskId();
        int maxAttempts = externalTaskCompleteMaxAttempts();
        boolean success = exitCode == 0;
        ExternalTask engineTask = success ? null : queryExternalTask(taskId);
        final Exception failureException;
        final RetryPlan retryPlan;
        if( success ) {
            failureException = null;
            retryPlan = new RetryPlan(0, 0);
        } else {
            failureException = resolveFailureException(job, slurmJobResult, engineTask);
            retryPlan = resolveRetryPlan(engineTask, failureException).orElseGet(() -> new RetryPlan(0, 0));
        }
        String errorMsg = getErrorMsg(job, slurmJobResult, failureException);
        Supplier<Boolean> step =
                () -> {
                    if( success ) {
                        return reportComplete(job, slurmJobResult);
                    }
                    return reportHandleFailure(job, slurmJobResult, failureException, retryPlan);
                };

        for( int attempt = 1; attempt <= maxAttempts; attempt++ ) {
            if( step.get() ) {
                if( slurmJobResult != null && exitCode != 0 ) {
                    slurmJobResult.setErrorMessage(errorMsg);
                }
                return new ReportOutcome(
                        true,
                        exitCode == 0 ? null : errorMsg);
            }
            if( attempt >= maxAttempts ) {
                log.error(
                        "Failed to report external task failure after {} attempts, taskId={}",
                        maxAttempts,
                        taskId);
                return new ReportOutcome(false, null);
            }
            log.warn(
                    "Failed to report external task failure, retrying ({}/{}), taskId={}",
                    attempt,
                    maxAttempts,
                    taskId);
            sleepBetweenExternalTaskRetries();
        }
        return new ReportOutcome(false, null);
    }

    private String getErrorMsg(SlurmJob job, SlurmJobResult slurmJobResult, Exception failureException) {
        String raw =
                failureException != null && StringUtils.isNotBlank(failureException.getMessage())
                        ? failureException.getMessage().trim()
                        : defaultFailureMessage(job, slurmJobResult);
        if( raw.length() > MAX_ERROR_MESSAGE_LENGTH ) {
            return raw.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return raw;
    }

    /**
     * Mongo 中存在对应 {@link SlurmJob} 时，用 {@link SlurmJob#getCompleteProcessLock()} 与 {@link SlurmJobStatus#Running}
     * 组合条件做原子抢锁；无文档时不加锁。
     */
    private LockOutcome acquireLock(String jobId) {
        long claimed = this.slurmJobRepository.claimCompleteProcessLock(jobId);
        if( claimed > 0 ) {
            return LockOutcome.ACQUIRED;
        }
        Optional<SlurmJob> opt = slurmJobRepository.findById(jobId);
        if( opt.isEmpty() ) {
            return LockOutcome.NO_TRACKING;
        }
        SlurmJob current = opt.get();
        SlurmJobStatus s = current.getStatus();
        if( s == SlurmJobStatus.Completed ) {
            return LockOutcome.ALREADY_COMPLETED;
        }
        if( s == SlurmJobStatus.Running && Boolean.TRUE.equals(current.getCompleteProcessLock()) ) {
            return LockOutcome.LOST_RACE;
        }
        if( s == SlurmJobStatus.Running ) {
            long retryClaim = slurmJobRepository.claimCompleteProcessLock(jobId);
            return retryClaim > 0 ? LockOutcome.ACQUIRED : LockOutcome.LOST_RACE;
        }
        return LockOutcome.LOST_RACE;
    }

    /** {@link #withOptimisticLock} 使用的抢锁 / 幂等判定结果 */
    private enum LockOutcome
    {
        /** 无 Mongo 跟踪或库中无此 jobId，直接走 Camunda 上报 */
        NO_TRACKING,
        /** 已抢到终态上报锁（{@link SlurmJob#getCompleteProcessLock()}） */
        ACQUIRED,
        /** 文档已是 {@link SlurmJobStatus#Completed}，幂等成功 */
        ALREADY_COMPLETED,
        /** 他处已持锁或状态异常 */
        LOST_RACE
    }

    /**
     * 单次 {@code complete}。{@code exitCode} 仅用于日志。
     */
    private boolean reportComplete(SlurmJob job, SlurmJobResult slurmJobResult) {
        try {
            externalTaskService.complete(
                    job.getExternalTaskId(), job.getWorkerId(), Collections.emptyMap(), Collections.emptyMap());
            log.debug(
                    "externalTask complete: taskId={},  exitCode={}",
                    job.getExternalTaskId(),
                    slurmJobResult.getExitCode());
            return true;
        } catch( Exception e ) {
            log.warn(
                    "complete failed: taskId={},  exitCode={}, error={}",
                    job.getExternalTaskId(),
                    slurmJobResult.getExitCode(),
                    e.getMessage());
            return false;
        }
    }

    private boolean reportHandleFailure(
            SlurmJob job, SlurmJobResult slurmJobResult, Exception failure, RetryPlan retryPlan) {
        String errMsg = failure.getMessage();
        if( errMsg == null || errMsg.isBlank() ) {
            errMsg = defaultFailureMessage(job, slurmJobResult);
        }
        String details = buildFailureDetails(job, slurmJobResult,failure);
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
            return true;
        } catch( Exception e ) {
            log.warn(
                    "handleFailure failed: taskId={}, workerId={}, retries={}, error={}",
                    job.getExternalTaskId(),
                    job.getWorkerId(),
                    retries,
                    e.getMessage());
            return false;
        }
    }

    private Exception resolveFailureException(
            SlurmJob job, SlurmJobResult slurmJobResult, ExternalTask engineTask) {
        String errorFilePath = job.getErrorFilePath();
        String errorFileContent = readErrorFileContent(errorFilePath);
        Map<String, Object> contextVariables;
        if( engineTask != null ) {
            contextVariables = safeExecutionVariables(engineTask.getExecutionId());
        } else {
            contextVariables = Map.of();
        }

        SlurmExternalTaskFailureResolver handler =
                Optional.ofNullable(handlerForTaskType(job.getTaskType()))
                        .orElse(defaultFailureResolver);
        try {
            return Optional.ofNullable(handler.resolve(job, errorFileContent, contextVariables))
                    .orElseGet(() -> defaultFailureResolver.resolve(job, errorFileContent, contextVariables));
        } catch( Exception ex ) {
            log.warn("SlurmExternalTaskFailureResolver failed, using default: {}", ex.toString());
            return defaultFailureResolver.resolve(job, errorFileContent, contextVariables);
        }
    }

    private Optional<RetryPlan> resolveRetryPlan(ExternalTask engineTask, Exception failure) {
        if( engineTask == null ) {
            return Optional.empty();
        }
        ExternalTaskRetryPlanner planner = externalTaskRetryPlanner.getIfAvailable();
        if( planner == null ) {
            return Optional.empty();
        }
        try {
            ExternalTaskImpl clientTask = DtoUtils.fromEngineExternalTask(engineTask);
            if( clientTask == null ) {
                return Optional.empty();
            }
            return Optional.of(planner.plan(clientTask, failure));
        } catch( Exception ex ) {
            log.warn("ExternalTaskRetryPlanner.plan failed, using retries=0: {}", ex.toString());
            return Optional.empty();
        }
    }



    private Map<String, Object> safeExecutionVariables(String executionId) {
        try {
            Map<String, Object> m = runtimeService.getVariables(executionId);
            return m != null ? m : Map.of();
        } catch( Exception e ) {
            return Map.of();
        }
    }

    private int externalTaskCompleteMaxAttempts() {
        return Math.max(1, slurmProperties.getExternalTaskCompleteMaxAttempts());
    }

    private SlurmExternalTaskFailureResolver handlerForTaskType(String taskTypeKey) {
        if( StringUtils.isBlank(taskTypeKey) ) {
            return null;
        }
        for( SlurmExternalTaskFailureResolver h : slurmExternalTaskFailureResolvers ) {
            if( taskTypeKey.equals(h.taskType()) ) {
                return h;
            }
        }
        return null;
    }

//    /**
//     * 与提交侧一致：优先 {@link SlurmJob#getTaskType()}；旧数据未持久化 taskType 时回退为 command 首词。
//     */
//    private String resolverTaskTypeKey(SlurmJob job) {
//        if( job == null ) {
//            return null;
//        }
//        if( StringUtils.isNotBlank(job.getTaskType()) ) {
//            return job.getTaskType().trim();
//        }
//        if( job.getCommand() == null || job.getCommand().isBlank() ) {
//            return null;
//        }
//        String[] parts = job.getCommand().trim().split("\\s+");
//        return parts.length > 0 ? parts[0] : null;
//    }

    private String readErrorFileContent(String errorFilePath) {
        if( errorFilePath == null || errorFilePath.isBlank() ) {
            return null;
        }
        String trimmed = errorFilePath.trim();
        if( !slurmService.isResolvedPathUnderWorkDirectory(trimmed) ) {
            log.warn(
                    "Skipping read of Slurm error file (path not under kiwi.bpm.slurm.work-directory): {}",
                    trimmed);
            return null;
        }
        try {
            String text = FileUtils.readFileToString(new File(trimmed), StandardCharsets.UTF_8);
            if( text.length() > MAX_ERROR_FILE_READ_LENGTH ) {
                return text.substring(0, MAX_ERROR_FILE_READ_LENGTH) + "\n...(truncated)";
            }
            return text;
        } catch( IOException e ) {
            log.debug("Could not read Slurm error file: {}", errorFilePath, e);
            return null;
        }
    }

    private String defaultFailureMessage(SlurmJob job, SlurmJobResult slurmJobResult) {
        String jn = job != null && job.getJobName() != null ? job.getJobName() : "";
        return "Slurm batch command exited with code " + slurmJobResult.getExitCode() + " (jobName=" + jn + ")";
    }

    private String buildFailureDetails(SlurmJob job, SlurmJobResult slurmJobResult, Exception failure) {
        StringBuilder sb = new StringBuilder();
        sb.append("slurmJob: jobId=")
                .append(job != null ? job.getJobId() : "")
                .append(", externalTaskId=")
                .append(job != null ? job.getExternalTaskId() : "")
                .append(", exitCode=")
                .append(slurmJobResult.getExitCode())
                .append('\n');
        StringWriter sw = new StringWriter();
        failure.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        if( stack.length() > 8000 ) {
            stack = stack.substring(0, 8000) + "...";
        }
        sb.append(stack);
        return sb.toString();
    }

    private void sleepBetweenExternalTaskRetries() {
        try {
            Thread.sleep(RETRY_INTERVAL_MS);
        } catch( InterruptedException ex ) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for external task retry", ex);
        }
    }

    private ExternalTask queryExternalTask(String taskId) {
        if( StringUtils.isBlank(taskId) ) {
            return null;
        }
        return externalTaskService.createExternalTaskQuery().externalTaskId(taskId).singleResult();
    }
}
