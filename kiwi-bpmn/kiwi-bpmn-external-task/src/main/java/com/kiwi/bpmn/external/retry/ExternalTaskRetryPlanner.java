package com.kiwi.bpmn.external.retry;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.engine.impl.bpmn.parser.FailedJobRetryConfiguration;
import org.camunda.bpm.engine.impl.calendar.DurationHelper;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.impl.util.ParseUtil;

import java.util.Date;
import java.util.List;

/**
 * 对齐 {@link org.camunda.bpm.engine.impl.cmd.DefaultJobRetryCmd#executeCustomStrategy} 的间隔索引与
 * {@link FailedJobRetryConfiguration} 初始化语义，计算下一次 {@code handleFailure} 的 retries / retryTimeout。
 */
public final class ExternalTaskRetryPlanner {

    /** 与 Job 默认失败递减语义对齐：parse 失败时的兜底剩余次数（近似 Job 默认 3 次）。 */
    private static final int FALLBACK_RETRIES_WHEN_UNPARSED = 3;

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

    private static ExternalTaskRetryPlan fallbackDecrement(ExternalTask task) {
        Integer cur = task.getRetries();
        int effective = cur == null ? FALLBACK_RETRIES_WHEN_UNPARSED : cur;
        return new ExternalTaskRetryPlan(Math.max(0, effective - 1), 0L);
    }
}
