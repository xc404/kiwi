package com.kiwi.bpmn.external.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External Task 统一重试：开关、可选默认周期（回退到引擎全局 {@code failedJobRetryTimeCycle}）。
 * <p>
 * 各活动上的重试时间轴以 BPMN 中 {@code camunda:failedJobRetryTimeCycle} 为准（见 {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryCycleResolver}）。
 */
@ConfigurationProperties(prefix = "kiwi.bpm.external-task-retry")
public class ExternalTaskRetryProperties {

    /**
     * 为 true 时注册 {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner}（含 BPMN 周期解析与失败规划），
     * {@link com.kiwi.bpmn.external.AbstractExternalTaskHandler} 失败路径走统一重试。
     */
    private boolean enabled = false;

    /**
     * 非空时作为 External Task 的全局默认 ISO 周期（当 BPMN 活动未配置 {@code failedJobRetryTimeCycle} 时使用）；
     * 为空则使用 {@code camunda.bpm.generic-properties.properties.failedJobRetryTimeCycle}。
     */
    private String defaultTimeCycle = "";

    /**
     * "非递减重试" 异常的退避周期（ISO-8601）。
     * <p>
     * 当失败异常链上的 {@link com.kiwi.bpmn.core.retry.IRetry} 实例返回
     * {@link com.kiwi.bpmn.core.retry.IRetry#decreaseRetries() decreaseRetries()} 为 {@code false}
     * 时，{@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner} 使用本 cycle 计算
     * {@code retryTimeoutMs}，并保留 {@code task.getRetries()}（首次失败时用 cycle 的 retries 值作为初值），
     * 即此类失败不消耗业务重试预算。
     * <p>
     * 默认 {@code R5/PT30S}（最多 5 次回退、每次 30 秒）。设为空时回退到 BPMN 节点配置或引擎默认 cycle 的第一个间隔；
     * 解析失败兜底为 30000ms。
     * <p>
     * 典型使用方：{@code com.kiwi.bpmn.component.slurm.SlurmOverloadedException}。
     */
    private String nonDecreasingRetryCycle = "R5/PT30S";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultTimeCycle() {
        return defaultTimeCycle;
    }

    public void setDefaultTimeCycle(String defaultTimeCycle) {
        this.defaultTimeCycle = defaultTimeCycle;
    }

    public String getNonDecreasingRetryCycle() {
        return nonDecreasingRetryCycle;
    }

    public void setNonDecreasingRetryCycle(String nonDecreasingRetryCycle) {
        this.nonDecreasingRetryCycle = nonDecreasingRetryCycle;
    }
}
