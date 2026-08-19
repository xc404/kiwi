package com.kiwi.project.bpm.designer.agent;

import lombok.Data;

/**
 * Agent run 对外状态（REST / 轮询）。
 */
@Data
public class DesignerAgentRunStatus {
    private String runId;
    private String targetProcessId;
    private boolean active;
    private String stage;
    private String editPlanJson;
    private String planDisplayJson;
    private String candidateXml;
    private String assistantReply;
    private String askMessage;
    private String pluginHintJson;
    private String issuesJson;
    private String errorMessage;
    private Boolean planSkipped;
}
