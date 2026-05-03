package com.kiwi.bpmn.core.retry;

/**
 * 一次失败上报后应写入引擎的剩余 retries 与下次可拉取延迟。
 */
public record RetryPlan(int nextRetries, long retryTimeoutMs) {}
