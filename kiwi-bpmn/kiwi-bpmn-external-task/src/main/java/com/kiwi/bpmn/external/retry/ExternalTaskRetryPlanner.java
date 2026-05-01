package com.kiwi.bpmn.external.retry;

import com.kiwi.bpmn.core.jobretry.JobRetryExceptionClassifier;
import com.kiwi.bpmn.core.jobretry.JobRetryFailureSupport;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.engine.impl.bpmn.parser.FailedJobRetryConfiguration;
import org.camunda.bpm.engine.impl.calendar.DurationHelper;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.impl.util.ParseUtil;

import java.util.Date;
import java.util.List;

/**
 * 外部任务失败后的重试规划：对齐 Job 的 {@code failedJobRetryTimeCycle} 间隔索引与
 * {@link FailedJobRetryConfiguration} 初始化语义，计算 {@code handleFailure} 的 retries / retryTimeout；
 * 并结合 {@link JobRetryExceptionClassifier}、OLE 链、BPMN 周期解析得到完整 {@link ExternalTaskRetryPlan}。
 */
public final class ExternalTaskRetryPlanner {

    private static final int OLE_FALLBACK_RETRIES = 3;

    /** 与 Job 默认失败递减语义对齐：parse 失败时的兜底剩余次数（近似 Job 默认 3 次）。 */
    private static final int FALLBACK_RETRIES_WHEN_UNPARSED = 3;

    private final JobRetryExceptionClassifier classifier;
    private final ExternalTaskRetryCycleResolver retryCycleResolver;
    private final String engineDefaultCycle;

    /**
     * @param classifier        可空：仅 {@link #plan(ExternalTask, Throwable)} 需要；单元测试若只测 {@link #plan(String, ExternalTask)} 可传 {@code null}
     * @param retryCycleResolver 可空：仅 {@link #plan(ExternalTask, Throwable)} 需要
     * @param engineDefaultCycle 可空：仅 {@link #plan(ExternalTask, Throwable)} 需要
     */
    public ExternalTaskRetryPlanner(
            JobRetryExceptionClassifier classifier,
            ExternalTaskRetryCycleResolver retryCycleResolver,
            String engineDefaultCycle) {
        this.classifier = classifier;
        this.retryCycleResolver = retryCycleResolver;
        this.engineDefaultCycle = engineDefaultCycle;
    }

    /**
     * 根据异常与任务状态计算下次上报失败时的重试参数；调用方再执行
     * {@code externalTaskService.handleFailure(task, msg, details, result.nextRetries(), result.retryTimeoutMs())}。
     */
    public ExternalTaskRetryPlan plan(ExternalTask task, Throwable failure) {
        if (classifier == null || retryCycleResolver == null || engineDefaultCycle == null) {
            throw new IllegalStateException(
                    "plan(task, failure) requires classifier, retryCycleResolver and engineDefaultCycle");
        }
        Throwable f = unwrap(failure);

        if (JobRetryFailureSupport.isOptimisticLockingOnChain(f)) {
            int keep = optimisticLockingKeepRetries(task);
            return new ExternalTaskRetryPlan(keep, 0L);
        }

        if (!classifier.shouldUseStandardFailedJobRetry(f)) {
            return new ExternalTaskRetryPlan(0, 0L);
        }

        String cycle = resolveCycle(task);
        return plan(cycle, task);
    }

    public ExternalTaskRetryPlan plan(String failedJobRetryTimeCycle, ExternalTask task) {
        FailedJobRetryConfiguration cfg = ParseUtil.parseRetryIntervals(failedJobRetryTimeCycle);
        if (cfg == null || cfg.getRetryIntervals() == null || cfg.getRetryIntervals().isEmpty()) {
            return fallbackDecrement(task);
        }

        List<String> intervals = cfg.getRetryIntervals();
        int intervalsCount = intervals.size();

        Integer currentRetries = task.getRetries();
        boolean firstFailure =
                currentRetries == null
                        && task.getErrorMessage() == null;

        int retriesBeforeIndexAndDecrement;
        if (firstFailure) {
            retriesBeforeIndexAndDecrement = cfg.getRetries();
        } else {
            retriesBeforeIndexAndDecrement = currentRetries != null ? currentRetries : cfg.getRetries();
        }

        int indexOfInterval =
                Math.max(
                        0,
                        Math.min(
                                intervalsCount - 1,
                                intervalsCount - (retriesBeforeIndexAndDecrement - 1)));

        String intervalExpression = intervals.get(indexOfInterval);
        long retryTimeoutMs;
        try {
            DurationHelper durationHelper = new DurationHelper(intervalExpression);
            Date next = durationHelper.getDateAfter();
            retryTimeoutMs =
                    next == null
                            ? 0L
                            : Math.max(0L, next.getTime() - ClockUtil.getCurrentTime().getTime());
        } catch (Exception e) {
            retryTimeoutMs = 0L;
        }

        int nextRetries = retriesBeforeIndexAndDecrement - 1;
        return new ExternalTaskRetryPlan(Math.max(0, nextRetries), retryTimeoutMs);
    }

    private int optimisticLockingKeepRetries(ExternalTask task) {
        Integer r = task.getRetries();
        if (r != null) {
            return r;
        }
        FailedJobRetryConfiguration cfg = ParseUtil.parseRetryIntervals(resolveCycle(task));
        if (cfg != null) {
            return cfg.getRetries();
        }
        return OLE_FALLBACK_RETRIES;
    }

    private String resolveCycle(ExternalTask task) {
        return retryCycleResolver.resolveFromBpmn(task).orElse(engineDefaultCycle);
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.util.concurrent.ExecutionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static ExternalTaskRetryPlan fallbackDecrement(ExternalTask task) {
        Integer cur = task.getRetries();
        int effective = cur == null ? FALLBACK_RETRIES_WHEN_UNPARSED : cur;
        return new ExternalTaskRetryPlan(Math.max(0, effective - 1), 0L);
    }
}
