package com.kiwi.bpmn.external.retry;

import com.kiwi.bpmn.core.retry.IRetry;
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

    /** 非递减重试分支退避周期解析失败 / 配置缺失时的兜底 retryTimeoutMs。 */
    private static final long NON_DECREASING_FALLBACK_RETRY_TIMEOUT_MS = 30_000L;

    /** 非递减重试分支首次失败（task.retries == null）时若 cycle 解析失败的兜底 retries 初值。 */
    private static final int NON_DECREASING_FALLBACK_INITIAL_RETRIES = 5;

    private final JobRetryExceptionClassifier classifier;
    private final ExternalTaskRetryCycleResolver retryCycleResolver;
    private final String engineDefaultCycle;
    private final String nonDecreasingRetryCycle;

    /**
     * @param classifier        ???? {@link #plan(ExternalTask, Throwable)} ???????????{@link #plan(String, ExternalTask)} ?? {@code null}
     * @param retryCycleResolver ???? {@link #plan(ExternalTask, Throwable)} ???
     * @param engineDefaultCycle ???? {@link #plan(ExternalTask, Throwable)} ???
     */
    public ExternalTaskRetryPlanner(
            JobRetryExceptionClassifier classifier,
            ExternalTaskRetryCycleResolver retryCycleResolver,
            String engineDefaultCycle) {
        this(classifier, retryCycleResolver, engineDefaultCycle, null);
    }

    /**
     * @param nonDecreasingRetryCycle 当异常链上的 {@link IRetry#decreaseRetries()} 返回 {@code false} 时
     *                                使用的退避周期（ISO-8601）；为空时回退到 BPMN / 引擎默认 cycle 的第一个间隔；
     *                                解析失败兜底为 {@link #NON_DECREASING_FALLBACK_RETRY_TIMEOUT_MS}。
     */
    public ExternalTaskRetryPlanner(
            JobRetryExceptionClassifier classifier,
            ExternalTaskRetryCycleResolver retryCycleResolver,
            String engineDefaultCycle,
            String nonDecreasingRetryCycle) {
        this.classifier = classifier;
        this.retryCycleResolver = retryCycleResolver;
        this.engineDefaultCycle = engineDefaultCycle;
        this.nonDecreasingRetryCycle =
                (nonDecreasingRetryCycle == null || nonDecreasingRetryCycle.isBlank())
                        ? null
                        : nonDecreasingRetryCycle.trim();
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

        IRetry retryMeta = JobRetryFailureSupport.findIRetryOnChain(f);
        if (retryMeta != null && !retryMeta.decreaseRetries()) {
            return planNonDecreasing(task);
        }

        if (!classifier.shouldUseStandardFailedJobRetry(f)) {
            return new RetryPlan(0, 0L);
        }

        String cycle = resolveCycle(task);
        return plan(cycle, task);
    }

    /**
     * 非递减重试分支：保留当前 {@code retries}（不消耗业务重试次数），按 {@link #nonDecreasingRetryCycle} 解析退避；
     * cycle 为空时回退到 BPMN / 引擎默认 cycle 的第一个间隔；解析失败兜底为
     * {@link #NON_DECREASING_FALLBACK_RETRY_TIMEOUT_MS}，初值兜底为 {@link #NON_DECREASING_FALLBACK_INITIAL_RETRIES}。
     */
    private RetryPlan planNonDecreasing(ExternalTask task) {
        String cycleExpr =
                nonDecreasingRetryCycle != null ? nonDecreasingRetryCycle : resolveCycle(task);
        FailedJobRetryConfiguration cfg =
                cycleExpr == null ? null : ParseUtil.parseRetryIntervals(cycleExpr);
        List<String> intervals = cfg == null ? null : cfg.getRetryIntervals();
        int cycleRetries =
                cfg == null ? NON_DECREASING_FALLBACK_INITIAL_RETRIES : cfg.getRetries();

        Integer currentRetries = task.getRetries();
        int nextRetries = currentRetries != null ? currentRetries : cycleRetries;

        long retryTimeoutMs = NON_DECREASING_FALLBACK_RETRY_TIMEOUT_MS;
        if (intervals != null && !intervals.isEmpty()) {
            String first = intervals.get(0);
            try {
                DurationHelper durationHelper = new DurationHelper(first);
                Date next = durationHelper.getDateAfter();
                if (next != null) {
                    retryTimeoutMs =
                            Math.max(0L, next.getTime() - ClockUtil.getCurrentTime().getTime());
                }
            } catch (Exception ignored) {
                // 保留兜底值
            }
        }
        return new RetryPlan(Math.max(0, nextRetries), retryTimeoutMs);
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
