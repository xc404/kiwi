package com.kiwi.bpmn.external.retry;

/**
 * 一次失败上报后应写入引擎的剩余 retries 与下次可拉取延迟。
 */
public record ExternalTaskRetryPlan(int nextRetries, long retryTimeoutMs) {
}
