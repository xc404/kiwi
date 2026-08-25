package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentGraphRuntime;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentRun;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.model.BpmProcess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
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
    private final BpmProcessDefinitionDao processDao;
    private final BpmProcessDefinitionService processDefinitionService;

    /** 进程内 SSE sink；Graph checkpoint 为 run 状态真相源。 */
    private final Map<String, DesignerAgentRun> runsById = new ConcurrentHashMap<>();
    private final Map<String, String> runIdByTarget = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AgentStreamEvent>> sinksByRunId = new ConcurrentHashMap<>();
    /** 已结束 run 的状态快照（checkpoint 不可用时的兜底）。 */
    private final Map<String, DesignerAgentRunStatus> terminalStatusByRunId = new ConcurrentHashMap<>();
    /** 同一流程被新指令取代的旧 runId。 */
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
        ensureNoActiveRun(targetProcessId);
        clearByTarget(targetProcessId);
        DesignerAgentRun run = newRun(scenario, targetProcessId, selectedElementId, baseBpmnXml, initiatorUserId);
        bindSink(run, eventSink);
        indexRun(run);
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

    public DesignerAgentRunStatus submitAction(String runId, DesignerAgentRunActionRequest action) {
        if (action == null || StringUtils.isBlank(action.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action.type 不能为空");
        }
        return switch (action.getType()) {
            case "confirm_plan" -> confirmPlan(
                    runId,
                    Boolean.TRUE.equals(action.getConfirmed()),
                    action.getEditedPlanJson(),
                    action.getCanvasBpmnXml());
            case "confirm_preview" -> confirmPreview(
                    runId, Boolean.TRUE.equals(action.getConfirmed()), action.getCanvasBpmnXml());
            case "answer" -> answerAsk(runId, action.getUserAnswer(), action.getCanvasBpmnXml());
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
        return run;
    }

    public void startRunExecution(String runId) {
        runGraphAsync(runId, () -> graphRuntime.start(requireRun(runId)));
    }

    private void runGraphAsync(String runId, Runnable task) {
        asyncExecutor.execute(() -> {
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
                run.setActive(false);
                run.setErrorMessage(e.getMessage());
                AgentStreamEvent err = AgentStreamEvent.of("error");
                err.setErrorMessage(e.getMessage());
                run.emit(err);
            } finally {
                finalizeIfTerminal(run);
            }
        });
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
                    "run 会话已失效（可能已被新指令取代）: " + runId);
        }
        DesignerAgentRunStatus terminal = terminalStatusByRunId.get(runId);
        if (terminal != null) {
            return terminal;
        }
        DesignerAgentRun cached = runsById.get(runId);
        if (cached != null) {
            graphRuntime.syncRunFromCheckpoint(cached);
            return toStatus(cached);
        }
        return graphRuntime.runFromCheckpoint(runId)
                .map(cp -> {
                    if (graphRuntime.isTerminal(cp)) {
                        DesignerAgentRunStatus status = toStatus(cp);
                        terminalStatusByRunId.putIfAbsent(runId, status);
                        return status;
                    }
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
        if (runId != null) {
            return statusByRunId(runId);
        }
        return findByTargetFromMemory(targetProcessId)
                .map(run -> {
                    graphRuntime.syncRunFromCheckpoint(run);
                    return toStatus(run);
                })
                .orElseGet(() -> {
                    DesignerAgentRunStatus empty = new DesignerAgentRunStatus();
                    empty.setTargetProcessId(targetProcessId);
                    empty.setActive(false);
                    return empty;
                });
    }

    public DesignerAgentRunStatus confirmPlan(String runId, boolean confirmed, String editedPlanJson, String canvasBpmnXml) {
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitPlan);
        if (confirmed) {
            applyCanvasBaseline(run, canvasBpmnXml);
        }
        runGraphAsync(runId, () -> graphRuntime.resumeAfterPlan(requireRun(runId), confirmed, editedPlanJson));
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus confirmPreview(String runId, boolean confirmed, String canvasBpmnXml) {
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
        } else {
            graphRuntime.resumeAfterPreviewReject(run);
            graphRuntime.syncRunFromCheckpoint(run);
        }
        finalizeIfTerminal(run);
        indexRun(run);
        if (graphRuntime.isTerminal(run) || !runId.equals(runIdByTarget.get(run.getTargetProcessId()))) {
            return toStatus(run);
        }
        return statusByRunId(runId);
    }

    public DesignerAgentRunStatus answerAsk(String runId, String userAnswer, String canvasBpmnXml) {
        if (StringUtils.isBlank(userAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userAnswer 不能为空");
        }
        DesignerAgentRun run = requireHumanGate(runId, AgentRunStage.AwaitAsk);
        applyCanvasBaseline(run, canvasBpmnXml);
        runGraphAsync(runId, () -> graphRuntime.resumeAfterAsk(requireRun(runId), userAnswer.trim()));
        return statusByRunId(runId);
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
                    "run 会话已失效（可能已被新指令取代）: " + runId);
        }
        DesignerAgentRun run = runsById.get(runId);
        if (run != null) {
            graphRuntime.syncRunFromCheckpoint(run);
            return run;
        }
        Optional<DesignerAgentRun> fromCheckpoint = graphRuntime.runFromCheckpoint(runId);
        if (fromCheckpoint.isPresent()) {
            DesignerAgentRun restored = fromCheckpoint.get();
            if (graphRuntime.isTerminal(restored)) {
                terminalStatusByRunId.putIfAbsent(runId, toStatus(restored));
            } else {
                indexRun(restored);
            }
            return restored;
        }
        if (terminalStatusByRunId.containsKey(runId)) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "run 已结束: " + runId);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId);
    }

    private void ensureNoActiveRun(String targetProcessId) {
        String existingRunId = runIdByTarget.get(targetProcessId);
        if (existingRunId == null) {
            return;
        }
        DesignerAgentRun existing = runsById.get(existingRunId);
        if (existing == null) {
            existing = graphRuntime.runFromCheckpoint(existingRunId).orElse(null);
        }
        if (existing == null || !existing.isActive() || graphRuntime.isTerminal(existing)) {
            return;
        }
        String stage = StringUtils.defaultIfBlank(existing.getStage(), "进行中");
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "当前流程已有进行中的 Agent 会话（" + stage + "），请先完成确认后再发送新指令");
    }

    private void indexRun(DesignerAgentRun run) {
        if (graphRuntime.isTerminal(run)) {
            return;
        }
        runsById.put(run.getRunId(), run);
        runIdByTarget.put(run.getTargetProcessId(), run.getRunId());
    }

    private void finalizeIfTerminal(DesignerAgentRun run) {
        if (!graphRuntime.isTerminal(run)) {
            return;
        }
        run.setActive(false);
        terminalStatusByRunId.put(run.getRunId(), toStatus(run));
        runsById.remove(run.getRunId());
        runIdByTarget.remove(run.getTargetProcessId());
        sinksByRunId.remove(run.getRunId());
    }

    private void clearByTarget(String targetProcessId) {
        String oldRunId = runIdByTarget.remove(targetProcessId);
        if (oldRunId == null) {
            return;
        }
        DesignerAgentRun old = runsById.remove(oldRunId);
        sinksByRunId.remove(oldRunId);
        supersededRunIds.add(oldRunId);
        graphRuntime.releaseThread(oldRunId);
        terminalStatusByRunId.remove(oldRunId);
        if (old != null) {
            old.setActive(false);
        }
    }

    private void bindSink(DesignerAgentRun run, Consumer<AgentStreamEvent> eventSink) {
        if (eventSink != null) {
            run.setEventSink(eventSink);
            sinksByRunId.put(run.getRunId(), eventSink);
        }
    }

    private Optional<DesignerAgentRun> findByTargetFromMemory(String targetProcessId) {
        return runsById.values().stream()
                .filter(r -> targetProcessId.equals(r.getTargetProcessId()))
                .findFirst();
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPM 设计器 Agent 未启用");
        }
    }

    private static DesignerAgentRunStatus toStatus(DesignerAgentRun run) {
        DesignerAgentRunStatus s = new DesignerAgentRunStatus();
        s.setRunId(run.getRunId());
        s.setTargetProcessId(run.getTargetProcessId());
        s.setActive(run.isActive());
        s.setStage(run.getStage());
        s.setEditPlanJson(run.getEditPlanJson());
        s.setPlanDisplayJson(run.getPlanDisplayJson());
        s.setCandidateXml(run.getCandidateXml());
        s.setAssistantReply(run.getAssistantReply());
        s.setAskMessage(run.getAskMessage());
        s.setPluginHintJson(run.getPluginHintJson());
        s.setIssuesJson(run.getIssuesJson());
        s.setErrorMessage(run.getErrorMessage());
        s.setPlanSkipped(run.isPlanSkipped());
        return s;
    }
}
