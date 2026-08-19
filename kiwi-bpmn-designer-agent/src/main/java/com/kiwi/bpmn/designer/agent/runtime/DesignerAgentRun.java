package com.kiwi.bpmn.designer.agent.runtime;

import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 单次 Agent run 内存状态。
 */
@Data
public class DesignerAgentRun {

    private String runId;
    private String targetProcessId;
    private String initiatorUserId;
    private String userScenario;
    private String userAnswer;
    private String selectedElementId;
    private String baseBpmnXml;
    private String stage;
    private boolean active = true;
    private String editPlanJson;
    private String candidateXml;
    private String assistantReply;
    private String issuesJson;
    private String askMessage;
    private String pluginHintJson;
    private String errorMessage;
    private int repairRound;
    private int toolStepCount;
    private boolean planSkipped;
    private boolean planConfirmed;
    private Boolean previewConfirmed;
    private Boolean installAccepted;
    private final List<AgentStreamEvent> events = new CopyOnWriteArrayList<>();
    private transient Consumer<AgentStreamEvent> eventSink;

    public void emit(AgentStreamEvent event) {
        if (event != null) {
            event.setRunId(runId);
            events.add(event);
            if (eventSink != null) {
                eventSink.accept(event);
            }
        }
    }

    public boolean isAwaitingHuman() {
        return com.kiwi.bpmn.designer.agent.model.AgentRunStage.AwaitPlan.equals(stage)
                || com.kiwi.bpmn.designer.agent.model.AgentRunStage.AwaitPreview.equals(stage)
                || com.kiwi.bpmn.designer.agent.model.AgentRunStage.AwaitInstall.equals(stage)
                || com.kiwi.bpmn.designer.agent.model.AgentRunStage.AwaitAsk.equals(stage);
    }
}
