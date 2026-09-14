package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentChatMessage;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentConversationHistoryUtils;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentGraphRuntime;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentRun;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.ai.mcp.KiwiMcpLoopbackAuthSupport;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class DesignerAgentSessionService {

    private final DesignerAgentProperties properties;
    private final DesignerAgentGraphRuntime graphRuntime;
    private final DesignerAgentAsyncExecutor asyncExecutor;
    private final DesignerAgentSessionStore sessionStore;
    private final BpmProcessDefinitionDao processDao;
    private final BpmProcessDefinitionService processDefinitionService;

    /** 进程内 SSE sink；Graph checkpoint 为 run 状态真相源。 */
    private final Map<String, DesignerAgentRun> runsById = new ConcurrentHashMap<>();
    private final Map<String, String> runIdByTarget = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AgentStreamEvent>> sinksByRunId = new ConcurrentHashMap<>();
    /** 已清空会话的 runId。 */
    private final Set<String> supersededRunIds = ConcurrentHashMap.newKeySet();

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public DesignerAgentRun startRun(
            String scenario,
            String targetProcessId,
            String selectedElementId,
            String baseBpmnXml,
            String initiatorUserId,
            Consumer<AgentStreamEvent> eventSink) {
        ensureEnabled();
        if (StringUtils.isBlank(scenario) || StringUtils.isBlank(targetProcessId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario 与 targetProcessId 不能为空");
        }
        if (sessionStore.findByTarget(targetProcessId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "流程已有 Agent 会话，请继续对话或清空会话");
        }
        ensureNotBusy(targetProcessId);
        DesignerAgentRun run = newRun(scenario, targetProcessId, selectedElementId, baseBpmnXml, initiatorUserId);
        bindSink(run, eventSink);
        indexRun(run);
        sessionStore.saveSession(run);
        return run;
    }

    /** 创建 run 并异步启动 Graph，不绑定事件流（契约四件套之 POST /runs）。 */
    public DesignerAgentRunStatus createRun(
            String scenario,
            String targetProcessId,
            String selectedElementId,
            String baseBpmnXml,
            String initiatorUserId) {
        DesignerAgentRun run = startRun(scenario, targetProcessId, selectedElementId, baseBpmnXml, initiatorUserId, null);
        startRunExecution(run.getRunId());
        return statusByRunId(run.getRunId());
    }

    public DesignerAgentRunStatus followUp(
            String runId,
            String message,
            String selectedElementId,
            String canvasBpmnXml) {
        if (StringUtils.isBlank(message)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        DesignerAgentRun run = requireRun(runId);
        if (!graphRuntime.canFollowUp(run)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前阶段不支持续聊: " + run.getStage());
        }
        ensureNotBusy(run.getTargetProcessId());
        if (StringUtils.isNotBlank(selectedElementId)) {
            run.setSelectedElementId(selectedElementId);
        }
        appendUserMessage(run, message.trim());
        sessionStore.saveSession(run);
        runGraphAsync(runId, () -> graphRuntime.resumeAfterFollowUp(requireRun(runId), message.trim(), canvasBpmnXml));
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus clearSession(String targetProcessId) {
        ensureEnabled();
        if (StringUtils.isBlank(targetProcessId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetProcessId 不能为空");
        }
        String runId = runIdByTarget.get(targetProcessId);
        if (runId == null) {
            runId = sessionStore.findByTarget(targetProcessId).map(DesignerAgentSessionDoc::getRunId).orElse(null);
        }
        if (runId != null) {
            supersededRunIds.add(runId);
            runsById.remove(runId);
            sinksByRunId.remove(runId);
            graphRuntime.releaseThread(runId);
        }
        runIdByTarget.remove(targetProcessId);
        sessionStore.deleteByTarget(targetProcessId);
        DesignerAgentRunStatus empty = new DesignerAgentRunStatus();
        empty.setTargetProcessId(targetProcessId);
        empty.setActive(false);
        return empty;
    }

    public DesignerAgentRunStatus submitAction(String runId, DesignerAgentRunActionRequest action) {
        if (action == null || StringUtils.isBlank(action.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action.type 不能为空");
        }
        return switch (action.getType()) {
            case "confirm_plan" -> confirmPlan(
                    runId,
                    Boolean.TRUE.equals(action.getConfirmed()),
                    action.getEditedPlanJson(),
                    action.getCanvasBpmnXml(),
                    action.getFeedbackText());
            case "confirm_preview" -> confirmPreview(
                    runId,
                    Boolean.TRUE.equals(action.getConfirmed()),
                    action.getCanvasBpmnXml(),
                    action.getFeedbackText());
            case "answer" -> answerAsk(runId, action.getUserAnswer(), action.getCanvasBpmnXml());
            case "submit_clarification" -> submitClarification(
                    runId,
                    action.getAnswers(),
                    action.getSkippedQuestionIds(),
                    action.getSupplementalText(),
                    action.getCanvasBpmnXml());
            case "resume_install" -> resumeInstall(runId);
            case "skip_install" -> skipInstall(runId);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "不支持的 action.type: " + action.getType());
        };
    }

    private DesignerAgentRun newRun(
            String scenario,
            String targetProcessId,
            String selectedElementId,
            String baseBpmnXml,
            String initiatorUserId) {
        DesignerAgentRun run = new DesignerAgentRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setTargetProcessId(targetProcessId);
        run.setUserScenario(scenario.trim());
        run.setSelectedElementId(selectedElementId);
        run.setBaseBpmnXml(baseBpmnXml);
        run.setInitiatorUserId(initiatorUserId);
        run.setConversationHistory(
                DesignerAgentConversationHistoryUtils.append(null, "user", scenario.trim()));
        return run;
    }

    public void startRunExecution(String runId) {
        runGraphAsync(runId, () -> graphRuntime.start(requireRun(runId)));
    }

    private void runGraphAsync(String runId, Runnable task) {
        String authToken = KiwiMcpLoopbackAuthSupport.captureCurrentToken();
        asyncExecutor.execute(() -> KiwiMcpLoopbackAuthSupport.runWithToken(authToken, () -> {
            DesignerAgentRun run;
            try {
                run = requireRun(runId);
            } catch (ResponseStatusException e) {
                return;
            }
            try {
                task.run();
            } catch (Exception e) {
                run.setStage(AgentRunStage.Error);
                run.setActive(true);
                run.setErrorMessage(e.getMessage());
                AgentStreamEvent err = AgentStreamEvent.of("error");
                err.setErrorMessage(e.getMessage());
                run.emit(err);
            } finally {
                graphRuntime.syncRunFromCheckpoint(run);
                indexRun(run);
                sessionStore.saveSession(run);
            }
        }));
    }

    public DesignerAgentRun attachStream(String runId, Consumer<AgentStreamEvent> eventSink) {
        DesignerAgentRun run = bindStream(runId, eventSink);
        replayBufferedEvents(run, eventSink);
        return run;
    }

    public DesignerAgentRun bindStream(String runId, Consumer<AgentStreamEvent> eventSink) {
        ensureEnabled();
        DesignerAgentRun run = requireRun(runId);
        bindSink(run, eventSink);
        return run;
    }

    public void replayBufferedEvents(DesignerAgentRun run, Consumer<AgentStreamEvent> eventSink) {
        if (run == null || eventSink == null) {
            return;
        }
        for (AgentStreamEvent buffered : run.getEvents()) {
            eventSink.accept(buffered);
        }
    }

    public DesignerAgentRunStatus statusByRunId(String runId) {
        if (supersededRunIds.contains(runId)) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "run 会话已失效（可能已被清空）: " + runId);
        }
        DesignerAgentRun cached = runsById.get(runId);
        if (cached != null) {
            graphRuntime.syncRunFromCheckpoint(cached);
            return toStatus(cached);
        }
        return graphRuntime.runFromCheckpoint(runId)
                .map(cp -> {
                    indexRun(cp);
                    return toStatus(cp);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId));
    }

    public DesignerAgentRunStatus statusOf(DesignerAgentRun run) {
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在");
        }
        return toStatus(run);
    }

    public DesignerAgentRunStatus statusByTarget(String targetProcessId) {
        String runId = runIdByTarget.get(targetProcessId);
        if (runId == null) {
            runId = sessionStore.findByTarget(targetProcessId).map(DesignerAgentSessionDoc::getRunId).orElse(null);
        }
        if (runId != null && !supersededRunIds.contains(runId)) {
            try {
                return statusByRunId(runId);
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() != HttpStatus.NOT_FOUND && ex.getStatusCode() != HttpStatus.GONE) {
                    throw ex;
                }
            }
        }
        List<DesignerAgentChatMessage> messages = sessionStore.messagesForTarget(targetProcessId);
        if (messages.isEmpty()) {
            DesignerAgentRunStatus empty = new DesignerAgentRunStatus();
            empty.setTargetProcessId(targetProcessId);
            empty.setActive(false);
            return empty;
        }
        DesignerAgentRunStatus orphan = new DesignerAgentRunStatus();
        orphan.setTargetProcessId(targetProcessId);
        orphan.setActive(false);
        orphan.setMessages(new ArrayList<>(messages));
        return orphan;
    }

    public DesignerAgentRunStatus confirmPlan(
            String runId, boolean confirmed, String editedPlanJson, String canvasBpmnXml, String feedbackText) {
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitPlan);
        if (confirmed) {
            applyCanvasBaseline(run, canvasBpmnXml);
        } else {
            if (StringUtils.isBlank(feedbackText)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "拒绝计划时请说明修改意见（输入框或反馈文字）");
            }
            appendUserMessage(run, feedbackText.trim());
        }
        sessionStore.saveSession(run);
        runGraphAsync(runId, () -> graphRuntime.resumeAfterPlan(requireRun(runId), confirmed, editedPlanJson, feedbackText));
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus confirmPreview(
            String runId, boolean confirmed, String canvasBpmnXml, String feedbackText) {
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitPreview);
        if (Boolean.TRUE.equals(confirmed)) {
            String toSave = resolveCanvasOrCandidate(run, canvasBpmnXml);
            if (StringUtils.isNotBlank(toSave)) {
                run.setCandidateXml(toSave);
            }
            graphRuntime.finishPreviewAccepted(run);
            if (StringUtils.isNotBlank(toSave)) {
                saveToProcess(run, toSave);
            }
            indexRun(run);
            sessionStore.saveSession(run);
            return toStatus(run);
        }
        if (StringUtils.isNotBlank(feedbackText)) {
            appendUserMessage(run, feedbackText.trim());
            sessionStore.saveSession(run);
            runGraphAsync(runId, () -> graphRuntime.resumeAfterPreviewReject(requireRun(runId), feedbackText));
            return statusByRunId(runId);
        }
        graphRuntime.resumeAfterPreviewReject(run, null);
        graphRuntime.syncRunFromCheckpoint(run);
        indexRun(run);
        sessionStore.saveSession(run);
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus submitClarification(
            String runId,
            Map<String, Object> answers,
            List<String> skippedQuestionIds,
            String supplementalText,
            String canvasBpmnXml) {
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitClarify);
        boolean hasAnswers = answers != null && !answers.isEmpty();
        boolean hasText = StringUtils.isNotBlank(supplementalText);
        if (!hasAnswers && !hasText) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请至少选择一项或填写补充说明");
        }
        applyCanvasBaseline(run, canvasBpmnXml);
        sessionStore.saveSession(run);
        runGraphAsync(runId, () -> {
            try {
                graphRuntime.resumeAfterClarify(
                        requireRun(runId),
                        answers != null ? answers : Map.of(),
                        skippedQuestionIds != null ? skippedQuestionIds : List.of(),
                        supplementalText);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus resumeInstall(String runId) {
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitInstall);
        sessionStore.saveSession(run);
        runGraphAsync(runId, () -> graphRuntime.resumeAfterInstall(requireRun(runId)));
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus skipInstall(String runId) {
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitInstall);
        sessionStore.saveSession(run);
        runGraphAsync(runId, () -> graphRuntime.skipInstall(requireRun(runId)));
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus answerAsk(String runId, String userAnswer, String canvasBpmnXml) {
        if (StringUtils.isBlank(userAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userAnswer 不能为空");
        }
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitAsk);
        applyCanvasBaseline(run, canvasBpmnXml);
        appendUserMessage(run, userAnswer.trim());
        sessionStore.saveSession(run);
        runGraphAsync(runId, () -> graphRuntime.resumeAfterAsk(requireRun(runId), userAnswer.trim()));
        return statusByRunId(runId);
    }

    private void appendUserMessage(DesignerAgentRun run, String text) {
        run.setConversationHistory(DesignerAgentConversationHistoryUtils.append(run.getConversationHistory(), "user", text));
    }

    private void applyCanvasBaseline(DesignerAgentRun run, String canvasBpmnXml) {
        if (StringUtils.isBlank(canvasBpmnXml)) {
            return;
        }
        graphRuntime.updateBaseBpmnXml(run, canvasBpmnXml);
    }

    private String resolveCanvasOrCandidate(DesignerAgentRun run, String canvasBpmnXml) {
        if (StringUtils.isNotBlank(canvasBpmnXml)) {
            return canvasBpmnXml.trim();
        }
        return StringUtils.trimToNull(run.getCandidateXml());
    }

    private DesignerAgentRun requireHumanGate(String runId, String expectedStage) {
        DesignerAgentRun run = requireRun(runId);
        if (!expectedStage.equals(run.getStage())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前不在 " + expectedStage + " 阶段: " + run.getStage());
        }
        return run;
    }

    private void saveToProcess(DesignerAgentRun run, String bpmnXml) {
        BpmProcess process = processDao.findById(run.getTargetProcessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程不存在"));
        process.setBpmnXml(bpmnXml);
        processDefinitionService.syncBpmnIdentity(process);
        process.setUpdatedTime(new Date());
        processDao.save(process);
    }

    private DesignerAgentRun requireRun(String runId) {
        if (supersededRunIds.contains(runId)) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "run 会话已失效（可能已被清空）: " + runId);
        }
        DesignerAgentRun run = runsById.get(runId);
        if (run != null) {
            graphRuntime.syncRunFromCheckpoint(run);
            return run;
        }
        Optional<DesignerAgentRun> fromCheckpoint = graphRuntime.runFromCheckpoint(runId);
        if (fromCheckpoint.isPresent()) {
            DesignerAgentRun restored = fromCheckpoint.get();
            indexRun(restored);
            return restored;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId);
    }

    private void ensureNotBusy(String targetProcessId) {
        DesignerAgentRun existing = resolveRunForTarget(targetProcessId);
        if (existing != null && graphRuntime.isBusyStage(existing.getStage())) {
            String stage = StringUtils.defaultIfBlank(existing.getStage(), "进行中");
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Agent 正在处理（" + stage + "），请稍候");
        }
    }

    private DesignerAgentRun resolveRunForTarget(String targetProcessId) {
        String runId = runIdByTarget.get(targetProcessId);
        if (runId == null) {
            runId = sessionStore.findByTarget(targetProcessId).map(DesignerAgentSessionDoc::getRunId).orElse(null);
        }
        if (runId == null || supersededRunIds.contains(runId)) {
            return null;
        }
        try {
            return requireRun(runId);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND || ex.getStatusCode() == HttpStatus.GONE) {
                return null;
            }
            throw ex;
        }
    }

    private void indexRun(DesignerAgentRun run) {
        if (run == null || StringUtils.isBlank(run.getRunId()) || StringUtils.isBlank(run.getTargetProcessId())) {
            return;
        }
        runsById.put(run.getRunId(), run);
        runIdByTarget.put(run.getTargetProcessId(), run.getRunId());
    }

    private void bindSink(DesignerAgentRun run, Consumer<AgentStreamEvent> eventSink) {
        if (eventSink != null) {
            run.setEventSink(eventSink);
            sinksByRunId.put(run.getRunId(), eventSink);
        }
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPM 设计器 Agent 未启用");
        }
    }

    private DesignerAgentRunStatus toStatus(DesignerAgentRun run) {
        DesignerAgentRunStatus s = new DesignerAgentRunStatus();
        s.setRunId(run.getRunId());
        s.setTargetProcessId(run.getTargetProcessId());
        s.setActive(run.isActive());
        s.setStage(run.getStage());
        s.setEditPlanJson(run.getEditPlanJson());
        s.setPlanDisplayJson(run.getPlanDisplayJson());
        if (AgentRunStage.AwaitPreview.equals(run.getStage())) {
            s.setCandidateXml(run.getCandidateXml());
        }
        s.setAssistantReply(run.getAssistantReply());
        s.setAskMessage(run.getAskMessage());
        s.setPluginHintJson(run.getPluginHintJson());
        s.setClarificationFormJson(run.getClarificationFormJson());
        s.setPendingHitlItemsJson(run.getPendingHitlItemsJson());
        s.setIssuesJson(run.getIssuesJson());
        s.setErrorMessage(run.getErrorMessage());
        s.setPlanSkipped(run.isPlanSkipped());
        s.setMessages(resolveMessages(run));
        return s;
    }

    private List<DesignerAgentChatMessage> resolveMessages(DesignerAgentRun run) {
        List<DesignerAgentChatMessage> messages =
                new ArrayList<>(DesignerAgentConversationHistoryUtils.toChatMessages(run.getConversationHistory()));
        if (messages.isEmpty() && StringUtils.isNotBlank(run.getTargetProcessId())) {
            messages = new ArrayList<>(sessionStore.messagesForTarget(run.getTargetProcessId()));
        }
        if (!messages.isEmpty()) {
            return messages;
        }
        return rebuildMessagesFromRunFields(run);
    }

    private List<DesignerAgentChatMessage> rebuildMessagesFromRunFields(DesignerAgentRun run) {
        List<DesignerAgentChatMessage> out = new ArrayList<>();
        if (StringUtils.isNotBlank(run.getUserScenario())) {
            DesignerAgentChatMessage user = new DesignerAgentChatMessage();
            user.setRole("user");
            user.setText(run.getUserScenario().trim());
            out.add(user);
        }
        String assistant = StringUtils.firstNonBlank(run.getAskMessage(), run.getAssistantReply());
        if (StringUtils.isNotBlank(assistant)) {
            DesignerAgentChatMessage assistantMsg = new DesignerAgentChatMessage();
            assistantMsg.setRole("assistant");
            assistantMsg.setText(assistant.trim());
            out.add(assistantMsg);
        }
        return out;
    }
}
