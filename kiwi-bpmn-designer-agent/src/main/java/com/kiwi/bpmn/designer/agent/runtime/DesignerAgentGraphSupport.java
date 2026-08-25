package com.kiwi.bpmn.designer.agent.runtime;

import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Graph 节点共用的 stage / SSE 辅助（实例方法，便于测试替换）。
 */
public class DesignerAgentGraphSupport {

    private static final Pattern ReadOnlyIntent = Pattern.compile(
            "解释|说明|干什么|做什么|概述|describe|explain", Pattern.CASE_INSENSITIVE);

    public boolean isReadOnly(String scenario) {
        return StringUtils.isNotBlank(scenario) && ReadOnlyIntent.matcher(scenario).find()
                && !scenario.contains("改") && !scenario.contains("加") && !scenario.contains("删");
    }

    public void emitStage(DesignerAgentRun run, String stage, String label, String detail) {
        run.setStage(stage);
        AgentStreamEvent e = AgentStreamEvent.of("stage");
        e.setStage(stage);
        e.setLabel(label);
        e.setDetail(detail);
        run.emit(e);
    }

    public void emitAwait(DesignerAgentRun run, String stage) {
        AgentStreamEvent e = AgentStreamEvent.of("await_human");
        e.setStage(stage);
        e.setAskMessage(run.getAskMessage());
        e.setPluginHintJson(run.getPluginHintJson());
        run.emit(e);
    }

    public void streamReply(DesignerAgentRun run) {
        if (StringUtils.isBlank(run.getAssistantReply())) {
            return;
        }
        appendConversation(run, "assistant", run.getAssistantReply());
        AgentStreamEvent text = AgentStreamEvent.of("text_delta");
        text.setDelta(run.getAssistantReply());
        run.emit(text);
    }

    public void appendConversation(DesignerAgentRun run, String role, String text) {
        if (run == null || StringUtils.isBlank(text)) {
            return;
        }
        run.setConversationHistory(
                DesignerAgentConversationHistoryUtils.append(run.getConversationHistory(), role, text));
    }

    public void finish(DesignerAgentRun run) {
        enterFollowUp(run);
    }

    /** 本轮任务完成，会话保持 open，等待 follow-up。 */
    public void enterFollowUp(DesignerAgentRun run) {
        String reply = run.getAssistantReply();
        String previewXml = run.getCandidateXml();
        run.setStage(AgentRunStage.AwaitFollowUp);
        run.setActive(true);
        run.setPlanConfirmed(false);
        run.setPreviewConfirmed(null);
        run.setPreviewFeedbackReady(false);
        run.setEditPlanJson(null);
        run.setRejectedEditPlanJson(null);
        run.setPlanDisplayJson(null);
        run.setCandidateXml(null);
        run.setAskMessage(null);
        run.setIssuesJson(null);
        run.setPlanSkipped(false);
        run.setPersistRequested(false);
        if (StringUtils.isNotBlank(reply)) {
            appendConversation(run, "assistant", reply);
        }
        AgentStreamEvent done = AgentStreamEvent.of("done");
        done.setContent(reply);
        done.setCandidateXml(previewXml);
        done.setStage(AgentRunStage.AwaitFollowUp);
        run.emit(done);
    }

    public void fail(DesignerAgentRun run, String message) {
        run.setStage(AgentRunStage.Error);
        run.setActive(true);
        run.setErrorMessage(message);
        AgentStreamEvent err = AgentStreamEvent.of("error");
        err.setErrorMessage(message);
        run.emit(err);
    }
}
