package com.kiwi.bpmn.core.jobretry;

import org.camunda.bpm.engine.delegate.BpmnError;

/**
 * 表示「可重试 / 需由流程侧按错误码处理」的 BPMN 业务错误，继承自 {@link BpmnError}，并实现 {@link IRetry} 标记。
 */
public class RetryException extends BpmnError implements IRetry {

    public static final String DEFAULT_ERROR_CODE = "KIWI_RETRY";

    public RetryException() {
        super(DEFAULT_ERROR_CODE);
    }

    public RetryException(String errorCode) {
        super(errorCode);
    }

    public RetryException(String errorCode, String message) {
        super(errorCode, message);
    }

    public RetryException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public RetryException(String errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
