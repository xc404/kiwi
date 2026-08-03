package com.kiwi.project.ai.authoring;

import lombok.Data;

@Data
public class AiAuthoringValidationIssue {
    private String code;
    private String message;
    private String elementId;
    private String componentId;
    private String pluginHint;
    /** REPAIR | INSTALL | ASK */
    private String severity;

    public static AiAuthoringValidationIssue of(String code, String message, String severity) {
        AiAuthoringValidationIssue i = new AiAuthoringValidationIssue();
        i.setCode(code);
        i.setMessage(message);
        i.setSeverity(severity);
        return i;
    }
}
