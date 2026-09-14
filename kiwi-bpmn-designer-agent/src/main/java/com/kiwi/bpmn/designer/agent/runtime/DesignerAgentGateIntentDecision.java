package com.kiwi.bpmn.designer.agent.runtime;

import org.apache.commons.lang3.StringUtils;

/**
 * 闸门意图解析结果。
 *
 * @param accepted 是否接受（批准计划 / 确认保存预览）
 * @param feedback 拒绝或要求修改时的说明（可空）
 * @param error 无法解析或模型不可用时的人类可读错误（非空则无效）
 */
public record DesignerAgentGateIntentDecision(boolean accepted, String feedback, String error) {

    public static DesignerAgentGateIntentDecision accept() {
        return new DesignerAgentGateIntentDecision(true, null, null);
    }

    public static DesignerAgentGateIntentDecision reject(String feedback) {
        return new DesignerAgentGateIntentDecision(false, StringUtils.trimToNull(feedback), null);
    }

    public static DesignerAgentGateIntentDecision failed(String error) {
        return new DesignerAgentGateIntentDecision(false, null, error);
    }

    public boolean ok() {
        return StringUtils.isBlank(error);
    }
}
