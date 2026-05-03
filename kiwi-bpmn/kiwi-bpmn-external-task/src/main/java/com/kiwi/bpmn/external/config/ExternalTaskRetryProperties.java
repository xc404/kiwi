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
}
