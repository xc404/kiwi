package com.kiwi.bpmn.designer.agent.runtime;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolTraceContext;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Graph 运行时：启动、resume、从 checkpoint 投影状态。
 */
@Component
@Slf4j
public class DesignerAgentGraphRuntime {

    private final DesignerAgentGraphFactory graphFactory;
    private final DesignerAgentStateMapper stateMapper;
    private final BaseCheckpointSaver checkpointSaver;
    private final DesignerAgentRunBinding runBinding;
    private final DesignerAgentGraphSupport graphSupport = new DesignerAgentGraphSupport();

    private CompiledGraph compiledGraph;

    public DesignerAgentGraphRuntime(
            DesignerAgentGraphFactory graphFactory,
            DesignerAgentStateMapper stateMapper,
            BaseCheckpointSaver checkpointSaver,
            DesignerAgentRunBinding runBinding) {
        this.graphFactory = graphFactory;
        this.stateMapper = stateMapper;
        this.checkpointSaver = checkpointSaver;
        this.runBinding = runBinding;
    }

    @PostConstruct
    void initGraph() throws GraphStateException {
        this.compiledGraph = graphFactory.compile();
    }

    public void start(DesignerAgentRun run) {
        RunnableConfig config = runBinding.bind(run.getRunId(), run);
        execute(run, config, stateMapper.toInputs(run), false);
    }

    public void resumeAfterPlan(DesignerAgentRun run, boolean confirmed, String editedPlanJson) {
        resumeAfterPlan(run, confirmed, editedPlanJson, null);
    }

    public void resumeAfterPlan(DesignerAgentRun run, boolean confirmed, String editedPlanJson, String feedbackText) {
        Map<String, Object> updates = new HashMap<>();
        if (confirmed) {
            updates.put(DesignerAgentStateKeys.PlanConfirmed, true);
            if (StringUtils.isNotBlank(editedPlanJson)) {
                updates.put(DesignerAgentStateKeys.EditPlanJson, editedPlanJson);
                run.setEditPlanJson(editedPlanJson);
            }
        } else {
            updates.put(DesignerAgentStateKeys.PlanConfirmed, false);
            if (StringUtils.isNotBlank(run.getEditPlanJson())) {
                run.setRejectedEditPlanJson(run.getEditPlanJson());
                updates.put(DesignerAgentStateKeys.RejectedEditPlanJson, run.getEditPlanJson());
            }
            String scenario = DesignerAgentPlanFeedbackHelper.buildReplanScenario(
                    run.getUserScenario(),
                    run.getPlanDisplayJson(),
                    run.getAssistantReply(),
                    feedbackText);
            updates.put(DesignerAgentStateKeys.UserScenario, scenario);
            run.setUserScenario(scenario);
        }
        String asNode = confirmed ? DesignerAgentGraphNodes.Apply : DesignerAgentGraphNodes.Generate;
        resume(run, updates, asNode);
    }

    public void resumeAfterAsk(DesignerAgentRun run, String answer) {
        run.setAskMessage(null);
        run.setUserScenario(answer);
        Map<String, Object> updates = new HashMap<>();
        updates.put(DesignerAgentStateKeys.UserScenario, answer);
        updates.put(DesignerAgentStateKeys.AskMessage, null);
        resume(run, updates, DesignerAgentGraphNodes.HumanAsk);
    }

    public void resumeAfterPreviewReject(DesignerAgentRun run) {
        resumeAfterPreviewReject(run, null);
    }

    public void resumeAfterPreviewReject(DesignerAgentRun run, String feedbackText) {
        run.setPreviewConfirmed(false);
        Map<String, Object> updates = new HashMap<>();
        updates.put(DesignerAgentStateKeys.PreviewConfirmed, false);
        if (StringUtils.isNotBlank(feedbackText)) {
            String trimmed = feedbackText.trim();
            run.setUserScenario(trimmed);
            run.setAskMessage(null);
            run.setPreviewFeedbackReady(true);
            updates.put(DesignerAgentStateKeys.UserScenario, trimmed);
            updates.put(DesignerAgentStateKeys.AskMessage, null);
            updates.put(DesignerAgentStateKeys.PreviewFeedbackReady, true);
        } else {
            run.setPreviewFeedbackReady(false);
            updates.put(DesignerAgentStateKeys.PreviewFeedbackReady, false);
        }
        resume(run, updates, DesignerAgentGraphNodes.HumanPreview);
    }

    public void finishPreviewAccepted(DesignerAgentRun run) {
        run.setPreviewConfirmed(true);
        run.setPersistRequested(true);
        if (StringUtils.isNotBlank(run.getCandidateXml())) {
            run.setBaseBpmnXml(run.getCandidateXml());
        }
        enterFollowUpState(run);
    }

    public void resumeAfterFollowUp(DesignerAgentRun run, String message, String canvasBpmnXml) {
        String trimmed = message.trim();
        String history = DesignerAgentConversationHistoryUtils.append(run.getConversationHistory(), "user", trimmed);
        run.setConversationHistory(history);
        run.setUserScenario(trimmed);
        run.setErrorMessage(null);
        run.setRepairRound(0);
        run.setRejectedEditPlanJson(null);
        run.setPreviewFeedbackReady(false);
        run.setPlanConfirmed(false);
        run.setPreviewConfirmed(null);
        if (StringUtils.isNotBlank(canvasBpmnXml)) {
            run.setBaseBpmnXml(canvasBpmnXml.trim());
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put(DesignerAgentStateKeys.ConversationHistory, history);
        updates.put(DesignerAgentStateKeys.UserScenario, trimmed);
        updates.put(DesignerAgentStateKeys.ErrorMessage, null);
        updates.put(DesignerAgentStateKeys.RepairRound, 0);
        updates.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteIngest);
        if (StringUtils.isNotBlank(run.getBaseBpmnXml())) {
            updates.put(DesignerAgentStateKeys.BaseBpmnXml, run.getBaseBpmnXml());
        }
        resume(run, updates, DesignerAgentGraphNodes.HumanFollowUp);
    }

    public void enterFollowUpState(DesignerAgentRun run) {
        graphSupport.enterFollowUp(run);
        syncCheckpointAtFollowUp(run);
    }

    private void syncCheckpointAtFollowUp(DesignerAgentRun run) {
        if (run == null || StringUtils.isBlank(run.getRunId())) {
            return;
        }
        RunnableConfig config = RunnableConfig.builder().threadId(run.getRunId()).build();
        Optional<StateSnapshot> snapshot = compiledGraph.stateOf(config);
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            RunnableConfig bound = runBinding.resume(run.getRunId(), run);
            String node = snapshot.get().node();
            compiledGraph.updateState(
                    bound,
                    stateMapper.followUpCheckpointUpdates(run),
                    node);
        } catch (Exception e) {
            log.warn("sync follow-up checkpoint failed runId={}", run.getRunId(), e);
        }
    }

    public Optional<DesignerAgentRun> runFromCheckpoint(String runId) {
        RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
        Optional<StateSnapshot> snapshot = compiledGraph.stateOf(config);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(stateMapper.newRunFrom(snapshot.get().state().data()));
    }

    public void releaseThread(String runId) {
        if (StringUtils.isBlank(runId)) {
            return;
        }
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
            checkpointSaver.release(config);
        } catch (Exception e) {
            log.debug("release checkpoint failed runId={}", runId, e);
        }
    }

    public void syncRunFromCheckpoint(DesignerAgentRun run) {
        if (run == null || StringUtils.isBlank(run.getRunId())) {
            return;
        }
        runFromCheckpoint(run.getRunId()).ifPresent(snapshot -> {
            var sink = run.getEventSink();
            stateMapper.applyToRun(stateMapper.toInputs(snapshot), run);
            run.setEventSink(sink);
        });
    }

    /** 用户手动改画布后，将最新 XML 写回 run 与 Graph checkpoint。 */
    public void updateBaseBpmnXml(DesignerAgentRun run, String canvasBpmnXml) {
        if (run == null || StringUtils.isBlank(canvasBpmnXml)) {
            return;
        }
        String xml = canvasBpmnXml.trim();
        run.setBaseBpmnXml(xml);
        RunnableConfig config = RunnableConfig.builder().threadId(run.getRunId()).build();
        Optional<StateSnapshot> snapshot = compiledGraph.stateOf(config);
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            RunnableConfig bound = runBinding.resume(run.getRunId(), run);
            compiledGraph.updateState(bound, Map.of(DesignerAgentStateKeys.BaseBpmnXml, xml), snapshot.get().node());
        } catch (Exception e) {
            log.warn("update baseBpmnXml failed runId={}", run.getRunId(), e);
        }
    }

    public boolean isTerminal(DesignerAgentRun run) {
        return false;
    }

    public boolean isBusyStage(String stage) {
        return AgentRunStage.Ingest.equals(stage)
                || AgentRunStage.Think.equals(stage)
                || AgentRunStage.Apply.equals(stage)
                || AgentRunStage.Validate.equals(stage)
                || AgentRunStage.Repair.equals(stage);
    }

    public boolean canFollowUp(DesignerAgentRun run) {
        if (run == null || StringUtils.isBlank(run.getStage())) {
            return false;
        }
        String stage = run.getStage();
        if (isBusyStage(stage)) {
            return false;
        }
        if (AgentRunStage.AwaitPlan.equals(stage)
                || AgentRunStage.AwaitPreview.equals(stage)
                || AgentRunStage.AwaitAsk.equals(stage)
                || AgentRunStage.AwaitInstall.equals(stage)) {
            return false;
        }
        return AgentRunStage.AwaitFollowUp.equals(stage)
                || AgentRunStage.Error.equals(stage)
                || AgentRunStage.Done.equals(stage);
    }

    private void resume(DesignerAgentRun run, Map<String, Object> updates, String asNode) {
        RunnableConfig config = runBinding.resume(run.getRunId(), run);
        try {
            config = compiledGraph.updateState(config, updates, asNode);
            config = config.withResume();
            config.context().put(DesignerAgentRunBinding.ContextKey, run);
            execute(run, config, Map.of(), true);
        } catch (Exception e) {
            log.error("designer agent resume failed runId={}", run.getRunId(), e);
            graphSupport.fail(run, e.getMessage());
        }
    }

    private void execute(DesignerAgentRun run, RunnableConfig config, Map<String, Object> inputs, boolean resume) {
        try {
            DesignerAgentToolTraceContext.bind(run);
            if (resume) {
                compiledGraph.stream(inputs, config).map(NodeOutput::state).blockLast();
            } else {
                compiledGraph.stream(inputs, config).map(NodeOutput::state).blockLast();
            }
            syncFromCheckpoint(run);
        } catch (Exception e) {
            log.error("designer agent graph failed runId={}", run.getRunId(), e);
            graphSupport.fail(run, e.getMessage());
        } finally {
            DesignerAgentToolTraceContext.clear();
        }
    }

    private void syncFromCheckpoint(DesignerAgentRun run) {
        runFromCheckpoint(run.getRunId()).ifPresent(snapshot -> stateMapper.applyToRun(stateMapper.toInputs(snapshot), run));
    }
}
