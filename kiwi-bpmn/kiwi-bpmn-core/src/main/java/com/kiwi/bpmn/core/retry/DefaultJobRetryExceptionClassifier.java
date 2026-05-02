package com.kiwi.bpmn.core.retry;

import org.springframework.stereotype.Component;

@Component
public class DefaultJobRetryExceptionClassifier implements JobRetryExceptionClassifier
{
    @Override
    public boolean shouldUseStandardFailedJobRetry(Throwable failure) {
        return JobRetryFailureSupport.isIRetryOnChain(failure);
    }
}
