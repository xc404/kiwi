package com.kiwi.bpmn.core.retry;

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

    /**
     * 在异常链上查找第一个 {@link IRetry} 实例，便于调用方读取其 {@link IRetry#decreaseRetries()} 等元信息。
     *
     * @return 命中实例；链上无 {@link IRetry} 时返回 {@code null}
     */
    public static IRetry findIRetryOnChain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IRetry r) {
                return r;
            }
        }
        return null;
    }
}
