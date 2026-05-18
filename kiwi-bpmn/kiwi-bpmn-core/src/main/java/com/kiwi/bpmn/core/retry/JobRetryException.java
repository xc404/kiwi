package com.kiwi.bpmn.core.retry;

import org.camunda.bpm.engine.delegate.BpmnError;

/**
 * 表示「可重试 / 需由流程侧按错误码处理」的 BPMN 业务错误，继承自 {@link BpmnError}，并实现 {@link IRetry} 标记。
 */
public class JobRetryException extends RuntimeException implements IRetry {


    public JobRetryException() {
    }

    public JobRetryException(String message) {
        super(message);
    }

    public JobRetryException(String message, Throwable cause) {
        super(message, cause);
    }

    public JobRetryException(Throwable cause) {
        super(cause);
    }

}
