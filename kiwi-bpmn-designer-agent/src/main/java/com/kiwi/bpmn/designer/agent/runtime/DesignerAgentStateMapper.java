package com.kiwi.bpmn.designer.agent.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DesignerAgentStateMapper {

    public Map<String, Object> toInputs(DesignerAgentRun run) {
        Map<String, Object> data = new HashMap<>();
        put(data, DesignerAgentStateKeys.RunId, run.getRunId());
        put(data, DesignerAgentStateKeys.TargetProcessId, run.getTargetProcessId());
        put(data, DesignerAgentStateKeys.InitiatorUserId, run.getInitiatorUserId());
        put(data, DesignerAgentStateKeys.UserScenario, run.getUserScenario());
        put(data, DesignerAgentStateKeys.SelectedElementId, run.getSelectedElementId());
        put(data, DesignerAgentStateKeys.BaseBpmnXml, run.getBaseBpmnXml());
        put(data, DesignerAgentStateKeys.Stage, run.getStage());
        data.put(DesignerAgentStateKeys.Active, run.isActive());
        put(data, DesignerAgentStateKeys.EditPlanJson, run.getEditPlanJson());
        put(data, DesignerAgentStateKeys.RejectedEditPlanJson, run.getRejectedEditPlanJson());
        put(data, DesignerAgentStateKeys.PlanDisplayJson, run.getPlanDisplayJson());
        put(data, DesignerAgentStateKeys.CandidateXml, run.getCandidateXml());
        put(data, DesignerAgentStateKeys.AssistantReply, run.getAssistantReply());
        put(data, DesignerAgentStateKeys.IssuesJson, run.getIssuesJson());
        put(data, DesignerAgentStateKeys.AskMessage, run.getAskMessage());
        put(data, DesignerAgentStateKeys.PluginHintJson, run.getPluginHintJson());
        put(data, DesignerAgentStateKeys.ErrorMessage, run.getErrorMessage());
        data.put(DesignerAgentStateKeys.RepairRound, run.getRepairRound());
        data.put(DesignerAgentStateKeys.ToolStepCount, run.getToolStepCount());
        data.put(DesignerAgentStateKeys.PlanSkipped, run.isPlanSkipped());
        data.put(DesignerAgentStateKeys.PlanConfirmed, run.isPlanConfirmed());
        data.put(DesignerAgentStateKeys.PreviewConfirmed, run.getPreviewConfirmed());
        data.put(DesignerAgentStateKeys.PreviewFeedbackReady, run.getPreviewFeedbackReady());
        data.put(DesignerAgentStateKeys.PersistRequested, run.getPersistRequested());
        put(data, DesignerAgentStateKeys.ConversationHistory, run.getConversationHistory());
        return data;
    }

    /** follow-up 时须显式清空 preview 相关字段，否则 checkpoint ReplaceStrategy 会保留旧 candidateXml。 */
    public Map<String, Object> followUpCheckpointUpdates(DesignerAgentRun run) {
        Map<String, Object> data = new HashMap<>(toInputs(run));
        data.put(DesignerAgentStateKeys.Stage, AgentRunStage.AwaitFollowUp);
        data.put(DesignerAgentStateKeys.CandidateXml, null);
        data.put(DesignerAgentStateKeys.EditPlanJson, null);
        data.put(DesignerAgentStateKeys.RejectedEditPlanJson, null);
        data.put(DesignerAgentStateKeys.PlanDisplayJson, null);
        data.put(DesignerAgentStateKeys.AskMessage, null);
        data.put(DesignerAgentStateKeys.IssuesJson, null);
        data.put(DesignerAgentStateKeys.PlanConfirmed, false);
        data.put(DesignerAgentStateKeys.PreviewFeedbackReady, false);
        data.put(DesignerAgentStateKeys.PersistRequested, false);
        return data;
    }

    public void applyToRun(Map<String, Object> data, DesignerAgentRun run) {
        if (data == null || run == null) {
            return;
        }
        run.setRunId(str(data, DesignerAgentStateKeys.RunId, run.getRunId()));
        run.setTargetProcessId(str(data, DesignerAgentStateKeys.TargetProcessId, run.getTargetProcessId()));
        run.setInitiatorUserId(str(data, DesignerAgentStateKeys.InitiatorUserId, run.getInitiatorUserId()));
        run.setUserScenario(str(data, DesignerAgentStateKeys.UserScenario, run.getUserScenario()));
        run.setSelectedElementId(str(data, DesignerAgentStateKeys.SelectedElementId, run.getSelectedElementId()));
        run.setBaseBpmnXml(str(data, DesignerAgentStateKeys.BaseBpmnXml, run.getBaseBpmnXml()));
        run.setStage(str(data, DesignerAgentStateKeys.Stage, run.getStage()));
        run.setActive(bool(data, DesignerAgentStateKeys.Active, run.isActive()));
        run.setEditPlanJson(str(data, DesignerAgentStateKeys.EditPlanJson, run.getEditPlanJson()));
        run.setRejectedEditPlanJson(
                str(data, DesignerAgentStateKeys.RejectedEditPlanJson, run.getRejectedEditPlanJson()));
        run.setPlanDisplayJson(str(data, DesignerAgentStateKeys.PlanDisplayJson, run.getPlanDisplayJson()));
        applyNullableString(data, DesignerAgentStateKeys.CandidateXml, run::setCandidateXml);
        run.setAssistantReply(str(data, DesignerAgentStateKeys.AssistantReply, run.getAssistantReply()));
        applyNullableString(data, DesignerAgentStateKeys.IssuesJson, run::setIssuesJson);
        applyNullableString(data, DesignerAgentStateKeys.AskMessage, run::setAskMessage);
        run.setPluginHintJson(str(data, DesignerAgentStateKeys.PluginHintJson, run.getPluginHintJson()));
        run.setErrorMessage(str(data, DesignerAgentStateKeys.ErrorMessage, run.getErrorMessage()));
        run.setRepairRound(intVal(data, DesignerAgentStateKeys.RepairRound, run.getRepairRound()));
        run.setToolStepCount(intVal(data, DesignerAgentStateKeys.ToolStepCount, run.getToolStepCount()));
        run.setPlanSkipped(bool(data, DesignerAgentStateKeys.PlanSkipped, run.isPlanSkipped()));
        run.setPlanConfirmed(bool(data, DesignerAgentStateKeys.PlanConfirmed, run.isPlanConfirmed()));
        run.setPreviewConfirmed((Boolean) data.getOrDefault(DesignerAgentStateKeys.PreviewConfirmed, run.getPreviewConfirmed()));
        run.setPreviewFeedbackReady(
                (Boolean) data.getOrDefault(DesignerAgentStateKeys.PreviewFeedbackReady, run.getPreviewFeedbackReady()));
        run.setPersistRequested((Boolean) data.getOrDefault(DesignerAgentStateKeys.PersistRequested, run.getPersistRequested()));
        run.setConversationHistory(
                str(data, DesignerAgentStateKeys.ConversationHistory, run.getConversationHistory()));
    }

    public void applyState(OverAllState state, DesignerAgentRun run) {
        if (state != null) {
            applyToRun(state.data(), run);
        }
    }

    public DesignerAgentRun newRunFrom(Map<String, Object> data) {
        DesignerAgentRun run = new DesignerAgentRun();
        applyToRun(data, run);
        return run;
    }

    private void put(Map<String, Object> data, String key, String value) {
        if (value != null) {
            data.put(key, value);
        }
    }

    private void applyNullableString(Map<String, Object> data, String key, Consumer<String> setter) {
        if (!data.containsKey(key)) {
            return;
        }
        Object v = data.get(key);
        if (v == null) {
            setter.accept(null);
            return;
        }
        String s = String.valueOf(v);
        setter.accept(StringUtils.isBlank(s) || "null".equals(s) ? null : s);
    }

    private String str(Map<String, Object> data, String key, String fallback) {
        Object v = data.get(key);
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v);
        return StringUtils.isBlank(s) || "null".equals(s) ? fallback : s;
    }

    private boolean bool(Map<String, Object> data, String key, boolean fallback) {
        Object v = data.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return fallback;
    }

    private int intVal(Map<String, Object> data, String key, int fallback) {
        Object v = data.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }
}
