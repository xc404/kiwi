package com.kiwi.bpmn.designer.agent.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.apply.EditPlanApplicator;
import com.kiwi.bpmn.designer.agent.apply.PlanSkipEvaluator;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.present.EditPlanPresenter;
import com.kiwi.bpmn.designer.agent.present.PlanDisplayView;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentPlanGenerator.GenerateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Designer Agent Graph 各节点业务实现。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DesignerAgentGraphNodes {

    public static final String Ingest = "ingest";
    public static final String Explain = "explain";
    public static final String PrepareClarify = "prepare_clarify";
    public static final String HumanClarify = "human_clarify";
    public static final String Generate = "generate";
    public static final String HumanPlan = "human_plan";
    public static final String Apply = "apply";
    public static final String Validate = "validate";
    public static final String HumanPreview = "human_preview";
    public static final String HumanAsk = "human_ask";
    public static final String HumanInstall = "human_install";
    public static final String HumanFollowUp = "human_follow_up";
    public static final String Fail = "fail";
    public static final String Finish = "finish";

    private final DesignerAgentProperties properties;
    private final EditPlanApplicator editPlanApplicator;
    private final PlanSkipEvaluator planSkipEvaluator;
    private final AssistantWorkflowValidator workflowValidator;
    private final ObjectMapper objectMapper;
    private final DesignerAgentPlanGenerator planGenerator;
    private final EditPlanPresenter editPlanPresenter;
    private final DesignerAgentClarificationPlanner clarificationPlanner;
    private final DesignerAgentStateMapper stateMapper;
    private final DesignerAgentGraphSupport graphSupport = new DesignerAgentGraphSupport();

    public Map<String, Object> ingest(OverAllState state, RunnableConfig config) throws Exception {
        DesignerAgentRun run = requireRun(state, config);
        graphSupport.emitStage(run, AgentRunStage.Ingest, "理解场景", "读取画布与指令");
        Map<String, Object> out = new HashMap<>();
        if (graphSupport.isReadOnly(run.getUserScenario())) {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteExplain);
        } else {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RoutePrepareClarify);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> prepareClarify(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        if (StringUtils.isNotBlank(run.getClarificationContextJson())
                || !properties.isClarifyBeforePlan()) {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
            mergeRun(out, run);
            return out;
        }
        var formJson = clarificationPlanner.buildFormJson(run.getUserScenario());
        if (formJson.isEmpty()) {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
        } else {
            run.setClarificationFormJson(formJson.get());
            run.setStage(AgentRunStage.AwaitClarify);
            AgentStreamEvent clarify = AgentStreamEvent.of("clarify_ready");
            clarify.setClarificationFormJson(run.getClarificationFormJson());
            clarify.setStage(AgentRunStage.AwaitClarify);
            run.emit(clarify);
            graphSupport.emitAwait(run, AgentRunStage.AwaitClarify);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanClarify);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> humanClarify(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> explain(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        graphSupport.emitStage(run, AgentRunStage.Think, "解读流程", "只读分析");
        String explanation = planGenerator.explainOnly(
                run.getUserScenario(), run.getBaseBpmnXml(), run.getSelectedElementId());
        run.setAssistantReply(explanation);
        Map<String, Object> out = new HashMap<>();
        out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteEnd);
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> generate(OverAllState state, RunnableConfig config) throws Exception {
        DesignerAgentRun run = requireRun(state, config);
        if (run.getRepairRound() > 0) {
            graphSupport.emitStage(run, AgentRunStage.Repair, "修复", "第 " + run.getRepairRound() + " 轮");
        } else {
            graphSupport.emitStage(run, AgentRunStage.Think, "规划变更", "检索组件并生成 EditPlan");
        }
        GenerateResult gen = planGenerator.generate(
                run.getUserScenario(),
                run.getBaseBpmnXml(),
                run.getSelectedElementId(),
                run.getIssuesJson(),
                null,
                run,
                run.getRejectedEditPlanJson());
        run.setRejectedEditPlanJson(null);
        Map<String, Object> out = new HashMap<>();
        if (gen == null) {
            graphSupport.fail(run, "Plan 生成失败");
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteFail);
            mergeRun(out, run);
            return out;
        }
        if (StringUtils.isNotBlank(gen.thinkingTrace())) {
            AgentStreamEvent thinking = AgentStreamEvent.of("thinking_delta");
            thinking.setDelta(gen.thinkingTrace());
            run.emit(thinking);
        }
        EditPlan plan = gen.editPlan();
        if (plan == null) {
            graphSupport.fail(run, StringUtils.defaultIfBlank(gen.summary(), "未能生成 EditPlan"));
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteFail);
            mergeRun(out, run);
            return out;
        }
        if (plan.getOperations() == null || plan.getOperations().isEmpty()) {
            routeToAsk(run, StringUtils.defaultIfBlank(plan.getSummary(), gen.summary()), out);
            mergeRun(out, run);
            return out;
        }
        run.setEditPlanJson(objectMapper.writeValueAsString(plan));
        run.setAssistantReply(StringUtils.defaultIfBlank(plan.getSummary(), gen.summary()));
        boolean skip = planSkipEvaluator.shouldSkipPlan(plan, run.getUserScenario());
        if (StringUtils.isBlank(run.getBaseBpmnXml())) {
            skip = false;
        }
        run.setPlanSkipped(skip);
        if (!skip) {
            PlanDisplayView display = editPlanPresenter.present(plan, run.getBaseBpmnXml(), gen.summary());
            run.setPlanDisplayJson(objectMapper.writeValueAsString(display));
            run.setStage(AgentRunStage.AwaitPlan);
            graphSupport.appendConversation(run, "assistant", display.getSummary());
            AgentStreamEvent planEvent = AgentStreamEvent.of("plan_ready");
            planEvent.setEditPlanJson(run.getEditPlanJson());
            planEvent.setPlanDisplayJson(run.getPlanDisplayJson());
            planEvent.setSummary(display.getSummary());
            planEvent.setPlanSkipped(false);
            planEvent.setStage(AgentRunStage.AwaitPlan);
            run.emit(planEvent);
            graphSupport.emitAwait(run, AgentRunStage.AwaitPlan);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanPlan);
        } else {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteApply);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> humanPlan(OverAllState state, RunnableConfig config) throws Exception {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        if (run.isPlanConfirmed()) {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteApply);
        } else {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        DesignerAgentRun run = requireRun(state, config);
        graphSupport.emitStage(run, AgentRunStage.Apply, "应用变更", "EditPlan → BPMN");
        EditPlan plan = parsePlan(StringUtils.defaultIfBlank(run.getEditPlanJson(), "{}"));
        Optional<String> xml = editPlanApplicator.apply(run.getBaseBpmnXml(), plan);
        Map<String, Object> out = new HashMap<>();
        if (xml.isEmpty()) {
            graphSupport.fail(run, "EditPlan 应用失败");
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteFail);
        } else {
            run.setCandidateXml(xml.get());
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteValidate);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> validate(OverAllState state, RunnableConfig config) throws Exception {
        DesignerAgentRun run = requireRun(state, config);
        graphSupport.emitStage(run, AgentRunStage.Validate, "校验", "结构、组件与参数");
        var result = workflowValidator.validate(run.getCandidateXml());
        run.setIssuesJson(objectMapper.writeValueAsString(result.getIssues()));
        AgentStreamEvent validation = AgentStreamEvent.of("validation");
        validation.setIssuesJson(run.getIssuesJson());
        run.emit(validation);
        String dispatch = workflowValidator.toDispatchCode(result.getIssues(), run.getRepairRound());
        Map<String, Object> out = new HashMap<>();
        if ("REPAIR".equals(dispatch) && run.getRepairRound() < properties.getMaxRepairRounds()) {
            run.setRepairRound(run.getRepairRound() + 1);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
        } else if ("INSTALL".equals(dispatch)) {
            run.setStage(AgentRunStage.AwaitInstall);
            var install = result.getIssues().stream()
                    .filter(i -> "INSTALL".equals(i.getSeverity()))
                    .findFirst();
            install.ifPresent(i -> {
                try {
                    run.setPluginHintJson(objectMapper.writeValueAsString(i));
                } catch (Exception ignored) {
                    run.setPluginHintJson(i.getMessage());
                }
            });
            graphSupport.emitAwait(run, AgentRunStage.AwaitInstall);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanInstall);
        } else if ("ASK".equals(dispatch)) {
            run.setStage(AgentRunStage.AwaitAsk);
            run.setAskMessage(result.getIssues().isEmpty()
                    ? "需要更多信息"
                    : result.getIssues().get(0).getMessage());
            graphSupport.emitAwait(run, AgentRunStage.AwaitAsk);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanAsk);
        } else if (isUnchangedPreview(run)) {
            routeToAsk(run, StringUtils.defaultIfBlank(run.getAssistantReply(), "需要更多信息才能继续"), out);
        } else {
            run.setStage(AgentRunStage.AwaitPreview);
            AgentStreamEvent preview = AgentStreamEvent.of("preview_ready");
            preview.setCandidateXml(run.getCandidateXml());
            preview.setStage(AgentRunStage.AwaitPreview);
            run.emit(preview);
            graphSupport.emitAwait(run, AgentRunStage.AwaitPreview);
            graphSupport.streamReply(run);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanPreview);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> humanPreview(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        if (Boolean.TRUE.equals(run.getPreviewConfirmed())) {
            run.setPersistRequested(true);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RoutePersistPreview);
        } else if (Boolean.FALSE.equals(run.getPreviewConfirmed())) {
            if (Boolean.TRUE.equals(run.getPreviewFeedbackReady())) {
                run.setPreviewFeedbackReady(false);
                out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
            } else {
                run.setStage(AgentRunStage.AwaitAsk);
                run.setAskMessage("已拒绝预览，请说明要如何调整");
                graphSupport.emitAwait(run, AgentRunStage.AwaitAsk);
                out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanAsk);
            }
        } else {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanPreview);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> humanAsk(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> humanInstall(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        if (Boolean.TRUE.equals(run.getInstallAccepted())) {
            run.setInstallAccepted(null);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteValidate);
        } else if (Boolean.TRUE.equals(run.getInstallSkipped())) {
            run.setInstallSkipped(null);
            run.setAssistantReply("已跳过插件安装，本次变更未继续校验。可安装插件后重新描述需求。");
            graphSupport.appendConversation(run, "assistant", run.getAssistantReply());
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanFollowUp);
        } else {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanInstall);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> humanFollowUp(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        Map<String, Object> out = new HashMap<>();
        Object route = state.data().get(DesignerAgentStateKeys.Route);
        if (DesignerAgentStateKeys.RouteIngest.equals(route)) {
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteIngest);
        } else {
            if (!AgentRunStage.AwaitFollowUp.equals(run.getStage())) {
                graphSupport.enterFollowUp(run);
            }
            graphSupport.emitAwait(run, AgentRunStage.AwaitFollowUp);
            out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanFollowUp);
        }
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> fail(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        if (!AgentRunStage.Error.equals(run.getStage()) && StringUtils.isNotBlank(run.getErrorMessage())) {
            graphSupport.fail(run, run.getErrorMessage());
        } else if (!AgentRunStage.Error.equals(run.getStage())) {
            graphSupport.fail(run, "Agent 执行失败");
        }
        Map<String, Object> out = new HashMap<>();
        out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteEnd);
        mergeRun(out, run);
        return out;
    }

    public Map<String, Object> finish(OverAllState state, RunnableConfig config) {
        DesignerAgentRun run = requireRun(state, config);
        if (!AgentRunStage.AwaitFollowUp.equals(run.getStage())) {
            graphSupport.enterFollowUp(run);
        }
        Map<String, Object> out = new HashMap<>();
        out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanFollowUp);
        mergeRun(out, run);
        return out;
    }

    private DesignerAgentRun requireRun(OverAllState state, RunnableConfig config) {
        if (config != null && config.context() != null) {
            Object bound = config.context().get(DesignerAgentRunBinding.ContextKey);
            if (bound instanceof DesignerAgentRun run) {
                stateMapper.applyState(state, run);
                return run;
            }
        }
        DesignerAgentRun run = stateMapper.newRunFrom(state.data());
        if (config != null) {
            config.context().put(DesignerAgentRunBinding.ContextKey, run);
        }
        return run;
    }

    private void mergeRun(Map<String, Object> out, DesignerAgentRun run) {
        out.putAll(stateMapper.toInputs(run));
    }

    private EditPlan parsePlan(String json) throws Exception {
        return objectMapper.readValue(json, EditPlan.class);
    }

    private void routeToAsk(DesignerAgentRun run, String message, Map<String, Object> out) {
        String ask = StringUtils.defaultIfBlank(message, "需要更多信息才能继续");
        run.setAssistantReply(ask);
        run.setStage(AgentRunStage.AwaitAsk);
        run.setAskMessage(ask);
        graphSupport.appendConversation(run, "assistant", ask);
        graphSupport.emitAwait(run, AgentRunStage.AwaitAsk);
        graphSupport.streamReply(run);
        out.put(DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteHumanAsk);
    }

    private boolean isUnchangedPreview(DesignerAgentRun run) {
        return StringUtils.isNotBlank(run.getAssistantReply())
                && StringUtils.equals(
                        StringUtils.trimToEmpty(run.getCandidateXml()),
                        StringUtils.trimToEmpty(run.getBaseBpmnXml()));
    }
}
