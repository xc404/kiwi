package com.kiwi.bpmn.external;

import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlan;
import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractExternalTaskHandler implements JavaDelegate, ExternalTaskHandler
{
    private ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner;

    /**
     * 可选：存在 {@link ExternalTaskRetryPlanner} Bean 时由 Spring 注入，子类无需在构造函数中传递。
     */
    @Autowired(required = false)
    public void setExternalTaskRetryPlanner(ObjectProvider<ExternalTaskRetryPlanner> provider) {
        this.externalTaskRetryPlanner = provider;
    }

    protected ObjectProvider<ExternalTaskRetryPlanner> getExternalTaskRetryPlanner() {
        return externalTaskRetryPlanner;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        ExternalTaskExecution externalTaskExecution = new ExternalTaskExecution(externalTask, externalTaskService);
        try {
            Date lockExpirationTime = externalTaskExecution.getLockExpirationTime();
            CompletableFuture<ExternalTaskAsyncResult> work = this.executeAsync(externalTaskExecution);
            CompletableFuture<Void> after =
                    work.thenApply(
                            outcome -> {
                                ExternalTaskAsyncResult o =
                                        outcome != null
                                                ? outcome
                                                : ExternalTaskAsyncResult.updateVariablesOnly();
                                if (o.finishExternalTask()) {
                                    externalTaskService.complete(
                                            externalTask, externalTaskExecution.getOutputVariable());
                                } else {
                                    externalTaskService.setVariables(
                                            externalTask, externalTaskExecution.getOutputVariable());
                                }
                                return null;
                            });
            if (lockExpirationTime == null) {
                after.get();
            } else {
                long durationMs = lockExpirationTime.getTime() - System.currentTimeMillis();
                if (durationMs <= 0) {
                    after.get();
                } else {
                    after.get(durationMs, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            Throwable failure = unwrapAsyncFailure(e);
            log.error(
                    "Failed to execute external task, topic: {}, id: {}",
                    externalTask.getTopicName(),
                    externalTask.getId(),
                    failure);
            String errorMessage = failureMessage(failure);
            String errorDetails = failureDetails(failure);
            int retries = 0;
            long retryTimeoutMs = 0L;
            ExternalTaskRetryPlanner retryPlanner = resolveRetryPlanner();
            if (retryPlanner != null) {
                ExternalTaskRetryPlan plan = retryPlanner.plan(externalTask, failure);
                retries = plan.nextRetries();
                retryTimeoutMs = plan.retryTimeoutMs();
            }
            externalTaskService.handleFailure(
                    externalTask, errorMessage, errorDetails, retries, retryTimeoutMs);
        }
    }

    /**
     * 将 {@link CompletableFuture} 常见的包装异常展开一层，便于分类器与日志。
     */
    private ExternalTaskRetryPlanner resolveRetryPlanner() {
        ObjectProvider<ExternalTaskRetryPlanner> provider = this.externalTaskRetryPlanner;
        return provider != null ? provider.getIfAvailable() : null;
    }

    private static Throwable unwrapAsyncFailure(Throwable e) {
        Throwable t = e;
        if (t instanceof ExecutionException && t.getCause() != null) {
            t = t.getCause();
        } else if (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static String failureMessage(Throwable f) {
        return f.getMessage() != null ? f.getMessage() : f.getClass().getName();
    }

    private static String failureDetails(Throwable f) {
        java.io.StringWriter sw = new java.io.StringWriter();
        f.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        if (s.length() > 8000) {
            return s.substring(0, 8000) + "...";
        }
        return s;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        long duration = 0;
        if( execution instanceof ExternalTaskExecution externalTaskExecution ) {
            Date lockExpirationTime = externalTaskExecution.getLockExpirationTime();
            duration = lockExpirationTime.getTime() - System.currentTimeMillis();
        }
        if( duration <= 0 ) {
            this.executeAsync(execution).get();
        } else {
            this.executeAsync(execution).get(duration, TimeUnit.MILLISECONDS);
        }

    }


    /**
     * 执行业务异步逻辑；返回值的 {@link ExternalTaskAsyncResult#finishExternalTask()} 决定随后调用
     * {@code complete} 还是仅 {@code setVariables}，输出变量一律来自 execution 上的写出映射（见 {@link ExternalTaskExecution}）。
     */
    public abstract CompletableFuture<ExternalTaskAsyncResult> executeAsync(DelegateExecution execution)
            throws Exception;
}
