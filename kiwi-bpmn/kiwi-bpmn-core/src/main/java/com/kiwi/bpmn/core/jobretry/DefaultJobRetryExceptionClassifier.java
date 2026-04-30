package com.kiwi.bpmn.core.jobretry;

import org.springframework.stereotype.Component;

@Component
public class DefaultJobRetryExceptionClassifier implements JobRetryExceptionClassifier
{
    @Override
    public boolean shouldUseStandardFailedJobRetry(Throwable failure) {
        return JobRetryFailureSupport.isIRetryOnChain(failure);
    }
}
