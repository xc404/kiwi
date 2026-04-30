package com.kiwi.bpmn.core.jobretry;

import org.camunda.bpm.engine.OptimisticLockingException;

public final class JobRetryFailureSupport {

    private JobRetryFailureSupport() {
    }

    public static boolean isOptimisticLockingOnChain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof OptimisticLockingException) {
                return true;
            }
        }
        return false;
    }

    public static boolean isIRetryOnChain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IRetry) {
                return true;
            }
        }
        return false;
    }
}
