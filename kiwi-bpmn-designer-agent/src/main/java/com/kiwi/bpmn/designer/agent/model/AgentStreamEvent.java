package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE / REST 对外事件。
 */
@Data
public class AgentStreamEvent {
    private String type;
    private String runId;
    private Long at;
    private String stage;
    private String label;
    private String detail;
    private String toolName;
    private String argsPreview;
    private String summary;
    private String delta;
    private String editPlanJson;
    private Boolean planSkipped;
    private String candidateXml;
    private String askMessage;
    private String pluginHintJson;
    private String issuesJson;
    private String content;
    private String errorMessage;
    private Map<String, Object> extra = new LinkedHashMap<>();

    public static AgentStreamEvent of(String type) {
        AgentStreamEvent e = new AgentStreamEvent();
        e.setType(type);
        e.setAt(System.currentTimeMillis());
        return e;
    }
}
