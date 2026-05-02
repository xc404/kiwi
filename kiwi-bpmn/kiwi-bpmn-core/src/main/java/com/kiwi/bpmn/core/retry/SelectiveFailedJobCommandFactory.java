package com.kiwi.bpmn.core.retry;

import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultFailedJobCommandFactory;
import org.camunda.bpm.engine.impl.jobexecutor.FailedJobCommandFactory;

import java.util.List;

/**
 * 根据 {@link JobRetryExceptionClassifier} 在默认 {@link org.camunda.bpm.engine.impl.cmd.DefaultJobRetryCmd}
 * 与立即耗尽 retries 之间二选一。
 */
final class SelectiveFailedJobCommandFactory implements FailedJobCommandFactory {

    private final FailedJobCommandFactory delegate;
    private final List<JobRetryExceptionClassifier> classifiers;

    SelectiveFailedJobCommandFactory(
            FailedJobCommandFactory delegate,
            List<JobRetryExceptionClassifier> classifiers) {
        this.delegate = delegate != null ? delegate : new DefaultFailedJobCommandFactory();
        this.classifiers = classifiers;
    }

    @Override
    public Command<Object> getCommand(String jobId, Throwable exception) {
        if (JobRetryFailureSupport.isOptimisticLockingOnChain(exception)) {
            return delegate.getCommand(jobId, exception);
        }
        if(classifiers.stream().anyMatch(classifier -> classifier.shouldUseStandardFailedJobRetry(exception))) {
            return delegate.getCommand(jobId, exception);
        }
        return new ExhaustJobRetriesCommand(jobId, exception);
    }
}
