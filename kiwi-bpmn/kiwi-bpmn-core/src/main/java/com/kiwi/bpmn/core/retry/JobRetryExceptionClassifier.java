package com.kiwi.bpmn.core.retry;

/**
 * 在异步 Job 失败时（{@link org.camunda.bpm.engine.impl.jobexecutor.FailedJobListener} 调用
 * {@link org.camunda.bpm.engine.impl.jobexecutor.FailedJobCommandFactory} 之前），决定
 * 是否走 Camunda 默认的 {@code DefaultJobRetryCmd}（按 {@code failedJobRetryTimeCycle} 递减重试）。
 * <p>
 * 返回 {@code false} 时，引擎将直接把 Job 的 retries 置为 0 并产生 failed-job incident，相当于「本类错误不参与引擎级重试」。
 * <p>
 * 说明：{@code BpmnParseListener} 只在部署解析期运行，拿不到运行期异常类型，无法实现「仅某类异常才重试」；
 * Camunda 也未对业务方暴露与 {@code FailedJobListener} 对等的可插拔 JobListener，因此本能力通过
 * 包装 {@link org.camunda.bpm.engine.impl.jobexecutor.FailedJobCommandFactory} 实现（与引擎内部
 * {@code FailedJobListener} 调用链一致）。
 */
@FunctionalInterface
public interface JobRetryExceptionClassifier {

    /**
     * @param failure Job 执行失败时收集到的异常（可能为包装类型）
     * @return {@code true} 使用默认失败 Job 重试逻辑；{@code false} 立即耗尽 retries 并产生 incident
     */
    boolean shouldUseStandardFailedJobRetry(Throwable failure);
}
