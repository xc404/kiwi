package com.kiwi.bpmn.external.retry;

import com.kiwi.bpmn.core.retry.JobRetryExceptionClassifier;
import com.kiwi.bpmn.core.retry.JobRetryFailureSupport;
import com.kiwi.bpmn.core.retry.RetryPlan;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.engine.impl.bpmn.parser.FailedJobRetryConfiguration;
import org.camunda.bpm.engine.impl.calendar.DurationHelper;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.impl.util.ParseUtil;

import java.util.Date;
import java.util.List;

/**
 * ????????????????Job ??{@code failedJobRetryTimeCycle} ??????
 * {@link FailedJobRetryConfiguration} ???????? {@code handleFailure} ??retries / retryTimeout??
 * ????{@link JobRetryExceptionClassifier}?OLE ??BPMN ???????? {@link RetryPlan}??
 */
public final class ExternalTaskRetryPlanner {

    private static final int OLE_FALLBACK_RETRIES = 3;

    /** ??Job ???????????parse ??????????????Job ?? 3 ????*/
    private static final int FALLBACK_RETRIES_WHEN_UNPARSED = 3;

    private final JobRetryExceptionClassifier classifier;
    private final ExternalTaskRetryCycleResolver retryCycleResolver;
    private final String engineDefaultCycle;

    /**
     * @param classifier        ???? {@link #plan(ExternalTask, Throwable)} ???????????{@link #plan(String, ExternalTask)} ?? {@code null}
     * @param retryCycleResolver ???? {@link #plan(ExternalTask, Throwable)} ???
     * @param engineDefaultCycle ???? {@link #plan(ExternalTask, Throwable)} ???
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
     * ??????????????????????????????
     * {@code externalTaskService.handleFailure(task, msg, details, result.nextRetries(), result.retryTimeoutMs())}??
     */
    public RetryPlan plan(ExternalTask task, Throwable failure) {
        if (classifier == null || retryCycleResolver == null || engineDefaultCycle == null) {
            throw new IllegalStateException(
                    "plan(task, failure) requires classifier, retryCycleResolver and engineDefaultCycle");
        }
        Throwable f = unwrap(failure);

        if (JobRetryFailureSupport.isOptimisticLockingOnChain(f)) {
            int keep = optimisticLockingKeepRetries(task);
            return new RetryPlan(keep, 0L);
        }

        if (!classifier.shouldUseStandardFailedJobRetry(f)) {
            return new RetryPlan(0, 0L);
        }

        String cycle = resolveCycle(task);
        return plan(cycle, task);
    }

    public RetryPlan plan(String failedJobRetryTimeCycle, ExternalTask task) {
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
        return new RetryPlan(Math.max(0, nextRetries), retryTimeoutMs);
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

    private static RetryPlan fallbackDecrement(ExternalTask task) {
        Integer cur = task.getRetries();
        int effective = cur == null ? FALLBACK_RETRIES_WHEN_UNPARSED : cur;
        return new RetryPlan(Math.max(0, effective - 1), 0L);
    }
}
