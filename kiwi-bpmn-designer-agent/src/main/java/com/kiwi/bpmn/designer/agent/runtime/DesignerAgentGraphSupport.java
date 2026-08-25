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
        AgentStreamEvent text = AgentStreamEvent.of("text_delta");
        text.setDelta(run.getAssistantReply());
        run.emit(text);
    }

    public void finish(DesignerAgentRun run) {
        run.setStage(AgentRunStage.Done);
        run.setActive(false);
        AgentStreamEvent done = AgentStreamEvent.of("done");
        done.setContent(run.getAssistantReply());
        done.setCandidateXml(run.getCandidateXml());
        done.setStage(AgentRunStage.Done);
        run.emit(done);
    }

    public void fail(DesignerAgentRun run, String message) {
        run.setStage(AgentRunStage.Error);
        run.setActive(false);
        run.setErrorMessage(message);
        AgentStreamEvent err = AgentStreamEvent.of("error");
        err.setErrorMessage(message);
        run.emit(err);
    }
}
