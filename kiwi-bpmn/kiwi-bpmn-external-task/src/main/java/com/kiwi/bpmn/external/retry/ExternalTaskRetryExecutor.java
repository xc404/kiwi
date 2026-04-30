package com.kiwi.bpmn.external.retry;

import com.kiwi.bpmn.core.jobretry.JobRetryExceptionClassifier;
import com.kiwi.bpmn.core.jobretry.JobRetryFailureSupport;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.impl.bpmn.parser.FailedJobRetryConfiguration;
import org.camunda.bpm.engine.impl.util.ParseUtil;

/**
 * 根据 {@link JobRetryExceptionClassifier} 与 {@link ExternalTaskRetryPlanner} 调用 {@link ExternalTaskService#handleFailure}。
 */
public final class ExternalTaskRetryExecutor {

    private static final int OLE_FALLBACK_RETRIES = 3;

    private final JobRetryExceptionClassifier classifier;
    private final ExternalTaskRetryPlanner planner;
    private final ExternalTaskRetryCycleResolver retryCycleResolver;
    private final String engineDefaultCycle;

    public ExternalTaskRetryExecutor(
            JobRetryExceptionClassifier classifier,
            ExternalTaskRetryPlanner planner,
            ExternalTaskRetryCycleResolver retryCycleResolver,
            String engineDefaultCycle) {
        this.classifier = classifier;
        this.planner = planner;
        this.retryCycleResolver = retryCycleResolver;
        this.engineDefaultCycle = engineDefaultCycle;
    }

    public void handleFailure(ExternalTask task, ExternalTaskService externalTaskService, Throwable failure) {
        Throwable f = unwrap(failure);
        String errorMessage = f.getMessage() != null ? f.getMessage() : f.getClass().getName();
        String errorDetails = stringifyDetails(f);

        if (JobRetryFailureSupport.isOptimisticLockingOnChain(f)) {
            int keep = optimisticLockingKeepRetries(task);
            externalTaskService.handleFailure(task, errorMessage, errorDetails, keep, 0L);
            return;
        }

        if (!classifier.shouldUseStandardFailedJobRetry(f)) {
            externalTaskService.handleFailure(task, errorMessage, errorDetails, 0, 0L);
            return;
        }

        String cycle = resolveCycle(task);
        ExternalTaskRetryPlan plan = planner.plan(cycle, task);
        externalTaskService.handleFailure(
                task, errorMessage, errorDetails, plan.nextRetries(), plan.retryTimeoutMs());
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

    private static String stringifyDetails(Throwable f) {
        java.io.StringWriter sw = new java.io.StringWriter();
        f.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        if (s.length() > 8000) {
            return s.substring(0, 8000) + "...";
        }
        return s;
    }
}
