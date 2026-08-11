package com.kiwi.bpmn.assistant;

import lombok.Data;

import java.util.UUID;

/**
 * AI 写工作流会话状态（替代元流程实例变量）。
 */
@Data
public class WriteWorkflowSession {

    private String sessionId = UUID.randomUUID().toString();
    private String scenario;
    private String targetProcessId;
    private String selectedElementId;
    private String initiatorUserId;
    private String keywordsJson;
    private String catalogJson;
    private String planIrJson;
    private String candidateXml;
    private String baseBpmnXml;
    private String assistantReply;
    private String issuesJson;
    private String dispatchCode;
    private int repairRound;
    private String pluginHintJson;
    private String askMessage;
    private String stage;
    private Boolean previewConfirmed;
    private Boolean installAccepted;
    private String userAnswer;
    private String errorMessage;
    private boolean active = true;

    public static WriteWorkflowSession newSession(
            String scenario,
            String targetProcessId,
            String selectedElementId,
            String baseBpmnXml,
            String initiatorUserId) {
        WriteWorkflowSession s = new WriteWorkflowSession();
        s.setScenario(scenario);
        s.setTargetProcessId(targetProcessId);
        s.setSelectedElementId(selectedElementId);
        s.setInitiatorUserId(initiatorUserId);
        s.setRepairRound(0);
        if (baseBpmnXml != null && !baseBpmnXml.isBlank()) {
            String xml = baseBpmnXml.trim();
            s.setBaseBpmnXml(xml);
            s.setCandidateXml(xml);
        }
        return s;
    }

    public WriteWorkflowStatus toStatus() {
        WriteWorkflowStatus status = new WriteWorkflowStatus();
        status.setSessionId(sessionId);
        status.setTargetProcessId(targetProcessId);
        status.setActive(active && !AssistantVariables.StageDone.equals(stage));
        status.setStage(stage);
        status.setDispatchCode(dispatchCode);
        status.setCandidateXml(candidateXml);
        status.setAssistantReply(assistantReply);
        status.setAskMessage(askMessage);
        status.setPluginHintJson(pluginHintJson);
        status.setIssuesJson(issuesJson);
        status.setCatalogJson(catalogJson);
        status.setErrorMessage(errorMessage);
        return status;
    }

    public boolean isAwaitingHuman() {
        return AssistantVariables.StageAwaitAsk.equals(stage)
                || AssistantVariables.StageAwaitPreview.equals(stage)
                || AssistantVariables.StageAwaitInstall.equals(stage);
    }
}
