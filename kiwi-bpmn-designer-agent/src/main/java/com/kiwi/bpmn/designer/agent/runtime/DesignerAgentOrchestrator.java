package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.apply.EditPlanApplicator;
import com.kiwi.bpmn.designer.agent.apply.PlanSkipEvaluator;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentPlanGenerator.GenerateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Agent 编排：ingest → 生成 EditPlan → plan 闸门 → patch → validate → preview。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DesignerAgentOrchestrator {

    private static final Pattern ReadOnlyIntent = Pattern.compile(
            "解释|说明|干什么|做什么|概述|describe|explain", Pattern.CASE_INSENSITIVE);

    private final DesignerAgentProperties properties;
    private final EditPlanApplicator editPlanApplicator;
    private final PlanSkipEvaluator planSkipEvaluator;
    private final AssistantWorkflowValidator workflowValidator;
    private final ObjectMapper objectMapper;
    private final DesignerAgentPlanGenerator planGenerator;

    public void runTurn(DesignerAgentRun run) {
        try {
            emitStage(run, AgentRunStage.Ingest, "理解场景", "读取画布与指令");
            if (isReadOnly(run.getUserScenario())) {
                runReadOnly(run);
                return;
            }
            emitStage(run, AgentRunStage.Think, "规划变更", "检索组件并生成 EditPlan");
            GenerateResult gen = planGenerator.generate(
                    run.getUserScenario(),
                    run.getBaseBpmnXml(),
                    run.getSelectedElementId(),
                    run.getIssuesJson(),
                    null,
                    run);
            if (StringUtils.isNotBlank(gen.thinkingTrace())) {
                AgentStreamEvent thinking = AgentStreamEvent.of("thinking_delta");
                thinking.setDelta(gen.thinkingTrace());
                run.emit(thinking);
            }
            EditPlan plan = gen.editPlan();
            if (plan == null) {
                fail(run, "未能生成 EditPlan");
                return;
            }
            run.setEditPlanJson(objectMapper.writeValueAsString(plan));
            run.setAssistantReply(StringUtils.defaultIfBlank(plan.getSummary(), gen.summary()));
            boolean skip = planSkipEvaluator.shouldSkipPlan(plan, run.getUserScenario());
            run.setPlanSkipped(skip);
            if (!skip) {
                run.setStage(AgentRunStage.AwaitPlan);
                AgentStreamEvent planEvent = AgentStreamEvent.of("plan_ready");
                planEvent.setEditPlanJson(run.getEditPlanJson());
                planEvent.setSummary(plan.getSummary());
                planEvent.setPlanSkipped(false);
                planEvent.setStage(AgentRunStage.AwaitPlan);
                run.emit(planEvent);
                AgentStreamEvent await = AgentStreamEvent.of("await_human");
                await.setStage(AgentRunStage.AwaitPlan);
                await.setDetail("请审阅变更计划并确认执行");
                run.emit(await);
                return;
            }
            applyAndValidate(run, plan);
        } catch (Exception e) {
            log.error("designer agent run failed", e);
            fail(run, e.getMessage());
        }
    }

    public void processPlanConfirmation(DesignerAgentRun run, boolean confirmed, String editedPlanJson) {
        if (!AgentRunStage.AwaitPlan.equals(run.getStage())) {
            return;
        }
        if (!confirmed) {
            run.setUserScenario(StringUtils.defaultIfBlank(run.getUserScenario(), "")
                    + " [用户拒绝了计划，请重新规划]");
            runTurn(run);
            return;
        }
        run.setPlanConfirmed(true);
        try {
            EditPlan plan = parsePlan(StringUtils.defaultIfBlank(editedPlanJson, run.getEditPlanJson()));
            applyAndValidate(run, plan);
        } catch (Exception e) {
            fail(run, e.getMessage());
        }
    }

    /**
     * @deprecated 由 {@link #processPlanConfirmation} + 异步 execute 替代，保留供兼容调用。
     */
    @Deprecated
    public void confirmPlan(DesignerAgentRun run, boolean confirmed, String editedPlanJson) {
        processPlanConfirmation(run, confirmed, editedPlanJson);
    }

    public void confirmPreview(DesignerAgentRun run, boolean confirmed) {
        run.setPreviewConfirmed(confirmed);
        if (!confirmed) {
            run.setStage(AgentRunStage.AwaitAsk);
            run.setAskMessage("已拒绝预览，请说明要如何调整");
            emitAwait(run, AgentRunStage.AwaitAsk);
            return;
        }
        finish(run);
    }

    public void prepareAfterAsk(DesignerAgentRun run, String answer) {
        run.setAskMessage(null);
        run.setUserScenario(answer);
    }

    /**
     * @deprecated 由 {@link #prepareAfterAsk} + {@link #runTurn} 异步组合替代。
     */
    @Deprecated
    public void continueAfterAsk(DesignerAgentRun run, String answer) {
        prepareAfterAsk(run, answer);
        runTurn(run);
    }

    private void applyAndValidate(DesignerAgentRun run, EditPlan plan) throws Exception {
        emitStage(run, AgentRunStage.Apply, "应用变更", "EditPlan → BPMN");
        Optional<String> xml = editPlanApplicator.apply(run.getBaseBpmnXml(), plan);
        if (xml.isEmpty()) {
            fail(run, "EditPlan 应用失败");
            return;
        }
        run.setCandidateXml(xml.get());
        validateLoop(run);
    }

    private void validateLoop(DesignerAgentRun run) throws Exception {
        while (true) {
            emitStage(run, AgentRunStage.Validate, "校验", "结构、组件与参数");
            var result = workflowValidator.validate(run.getCandidateXml());
            run.setIssuesJson(objectMapper.writeValueAsString(result.getIssues()));
            AgentStreamEvent validation = AgentStreamEvent.of("validation");
            validation.setIssuesJson(run.getIssuesJson());
            run.emit(validation);
            String dispatch = workflowValidator.toDispatchCode(result.getIssues(), run.getRepairRound());
            if ("REPAIR".equals(dispatch) && run.getRepairRound() < properties.getMaxRepairRounds()) {
                run.setRepairRound(run.getRepairRound() + 1);
                emitStage(run, AgentRunStage.Repair, "修复", "第 " + run.getRepairRound() + " 轮");
                GenerateResult gen = planGenerator.generate(
                        run.getUserScenario(),
                        run.getBaseBpmnXml(),
                        run.getSelectedElementId(),
                        run.getIssuesJson(),
                        null,
                        run);
                EditPlan plan = gen.editPlan();
                if (plan != null) {
                    applyAndValidate(run, plan);
                }
                return;
            }
            if ("INSTALL".equals(dispatch)) {
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
                emitAwait(run, AgentRunStage.AwaitInstall);
                return;
            }
            if ("ASK".equals(dispatch)) {
                run.setStage(AgentRunStage.AwaitAsk);
                run.setAskMessage(result.getIssues().isEmpty()
                        ? "需要更多信息"
                        : result.getIssues().get(0).getMessage());
                emitAwait(run, AgentRunStage.AwaitAsk);
                return;
            }
            run.setStage(AgentRunStage.AwaitPreview);
            AgentStreamEvent preview = AgentStreamEvent.of("preview_ready");
            preview.setCandidateXml(run.getCandidateXml());
            preview.setStage(AgentRunStage.AwaitPreview);
            run.emit(preview);
            emitAwait(run, AgentRunStage.AwaitPreview);
            streamReply(run);
            return;
        }
    }

    private void runReadOnly(DesignerAgentRun run) {
        emitStage(run, AgentRunStage.Think, "解读流程", "只读分析");
        String explanation = planGenerator.explainOnly(
                run.getUserScenario(), run.getBaseBpmnXml(), run.getSelectedElementId());
        run.setAssistantReply(explanation);
        run.setStage(AgentRunStage.Done);
        run.setActive(false);
        AgentStreamEvent done = AgentStreamEvent.of("done");
        done.setContent(explanation);
        done.setStage(AgentRunStage.Done);
        run.emit(done);
    }

    private void finish(DesignerAgentRun run) {
        run.setStage(AgentRunStage.Done);
        run.setActive(false);
        AgentStreamEvent done = AgentStreamEvent.of("done");
        done.setContent(run.getAssistantReply());
        done.setCandidateXml(run.getCandidateXml());
        done.setStage(AgentRunStage.Done);
        run.emit(done);
    }

    private void fail(DesignerAgentRun run, String message) {
        run.setStage(AgentRunStage.Error);
        run.setActive(false);
        run.setErrorMessage(message);
        AgentStreamEvent err = AgentStreamEvent.of("error");
        err.setErrorMessage(message);
        run.emit(err);
    }

    private void streamReply(DesignerAgentRun run) {
        if (StringUtils.isBlank(run.getAssistantReply())) {
            return;
        }
        AgentStreamEvent text = AgentStreamEvent.of("text_delta");
        text.setDelta(run.getAssistantReply());
        run.emit(text);
    }

    private void emitStage(DesignerAgentRun run, String stage, String label, String detail) {
        run.setStage(stage);
        AgentStreamEvent e = AgentStreamEvent.of("stage");
        e.setStage(stage);
        e.setLabel(label);
        e.setDetail(detail);
        run.emit(e);
    }

    private void emitAwait(DesignerAgentRun run, String stage) {
        AgentStreamEvent e = AgentStreamEvent.of("await_human");
        e.setStage(stage);
        e.setAskMessage(run.getAskMessage());
        e.setPluginHintJson(run.getPluginHintJson());
        run.emit(e);
    }

    private boolean isReadOnly(String scenario) {
        return StringUtils.isNotBlank(scenario) && ReadOnlyIntent.matcher(scenario).find()
                && !scenario.contains("改") && !scenario.contains("加") && !scenario.contains("删");
    }

    private EditPlan parsePlan(String json) throws Exception {
        return objectMapper.readValue(json, EditPlan.class);
    }
}
