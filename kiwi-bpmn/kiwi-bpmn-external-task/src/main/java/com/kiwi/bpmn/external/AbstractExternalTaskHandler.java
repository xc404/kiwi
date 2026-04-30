package com.kiwi.bpmn.external;

import com.kiwi.bpmn.external.retry.ExternalTaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractExternalTaskHandler implements JavaDelegate, ExternalTaskHandler
{
    private ObjectProvider<ExternalTaskRetryExecutor> externalTaskRetryExecutor;

    /**
     * 可选：存在 {@link ExternalTaskRetryExecutor} Bean 时由 Spring 注入，子类无需在构造函数中传递。
     */
    @Autowired(required = false)
    public void setExternalTaskRetryExecutor(ObjectProvider<ExternalTaskRetryExecutor> provider) {
        this.externalTaskRetryExecutor = provider;
    }

    protected ObjectProvider<ExternalTaskRetryExecutor> getExternalTaskRetryExecutor() {
        return externalTaskRetryExecutor;
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
            ExternalTaskRetryExecutor retryExecutor = resolveRetryExecutor();
            if (retryExecutor != null) {
                retryExecutor.handleFailure(externalTask, externalTaskService, failure);
            } else {
                externalTaskService.handleFailure(
                        externalTask.getId(),
                        failure.getMessage(),
                        failure.getLocalizedMessage(),
                        Optional.ofNullable(externalTask.getRetries()).orElse(0),
                        0L);
            }
        }
    }

    /**
     * 将 {@link CompletableFuture} 常见的包装异常展开一层，便于分类器与日志。
     */
    private ExternalTaskRetryExecutor resolveRetryExecutor() {
        ObjectProvider<ExternalTaskRetryExecutor> provider = this.externalTaskRetryExecutor;
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
