package com.kiwi.bpmn.assistant;

import lombok.Data;

@Data
public class AssistantValidationIssue {
    private String code;
    /** 触发该问题的生成/校验规则，供 repair 精确回修与评测聚合。 */
    private String ruleId;
    private String message;
    private String elementId;
    private String componentId;
    private String pluginHint;
    /** REPAIR | INSTALL | ASK */
    private String severity;

    public static AssistantValidationIssue of(String code, String message, String severity) {
        AssistantValidationIssue i = new AssistantValidationIssue();
        i.setCode(code);
        i.setMessage(message);
        i.setSeverity(severity);
        return i;
    }
}
