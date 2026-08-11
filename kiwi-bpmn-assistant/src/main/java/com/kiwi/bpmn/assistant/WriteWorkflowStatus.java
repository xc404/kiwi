package com.kiwi.bpmn.assistant;

import lombok.Data;

/**
 * AI 写工作流会话对外状态（面板 / Chat / REST）。
 */
@Data
public class WriteWorkflowStatus {

    private String sessionId;
    private String targetProcessId;
    private boolean active;
    private String stage;
    private String dispatchCode;
    private String candidateXml;
    private String assistantReply;
    private String askMessage;
    private String pluginHintJson;
    private String issuesJson;
    private String catalogJson;
    private String errorMessage;
}
