package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentOrchestrator;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentRun;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class DesignerAgentSessionService {

    private final DesignerAgentProperties properties;
    private final DesignerAgentOrchestrator orchestrator;
    private final BpmProcessDefinitionDao processDao;
    private final BpmProcessDefinitionService processDefinitionService;

    private final Map<String, DesignerAgentRun> runsById = new ConcurrentHashMap<>();
    private final Map<String, DesignerAgentRun> runsByTarget = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AgentStreamEvent>> sinksByRunId = new ConcurrentHashMap<>();
    private final Map<String, Runnable> pendingContinuations = new ConcurrentHashMap<>();

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
        clearByTarget(targetProcessId);
        DesignerAgentRun run = new DesignerAgentRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setTargetProcessId(targetProcessId);
        run.setUserScenario(scenario.trim());
        run.setSelectedElementId(selectedElementId);
        run.setBaseBpmnXml(baseBpmnXml);
        run.setInitiatorUserId(initiatorUserId);
        if (eventSink != null) {
            run.setEventSink(eventSink);
            sinksByRunId.put(run.getRunId(), eventSink);
        }
        put(run);
        executeAsync(run.getRunId());
        return run;
    }

    @Async
    public void executeAsync(String runId) {
        DesignerAgentRun run = runsById.get(runId);
        if (run == null) {
            return;
        }
        try {
            Runnable continuation = pendingContinuations.remove(runId);
            if (continuation != null) {
                continuation.run();
            } else {
                orchestrator.runTurn(run);
            }
        } catch (Exception e) {
            run.setStage(AgentRunStage.Error);
            run.setActive(false);
            run.setErrorMessage(e.getMessage());
            AgentStreamEvent err = AgentStreamEvent.of("error");
            err.setErrorMessage(e.getMessage());
            run.emit(err);
        } finally {
            if (AgentRunStage.Done.equals(run.getStage()) || AgentRunStage.Error.equals(run.getStage())) {
                sinksByRunId.remove(runId);
            }
            put(run);
        }
    }

    /**
     * 为已有 run 重新绑定 SSE sink（confirm-plan / answer 后续事件续推）。
     */
    public DesignerAgentRun attachStream(String runId, Consumer<AgentStreamEvent> eventSink) {
        ensureEnabled();
        DesignerAgentRun run = requireRun(runId);
        if (eventSink != null) {
            run.setEventSink(eventSink);
            sinksByRunId.put(runId, eventSink);
            for (AgentStreamEvent buffered : run.getEvents()) {
                eventSink.accept(buffered);
            }
        }
        return run;
    }

    public DesignerAgentRunStatus statusByRunId(String runId) {
        DesignerAgentRun run = runsById.get(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId);
        }
        return toStatus(run);
    }

    /** 由内存中的 run 对象构建状态（避免异步 run 已从 map 移除时 404）。 */
    public DesignerAgentRunStatus statusOf(DesignerAgentRun run) {
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在");
        }
        return toStatus(run);
    }

    public DesignerAgentRunStatus statusByTarget(String targetProcessId) {
        DesignerAgentRun run = runsByTarget.get(targetProcessId);
        if (run == null) {
            DesignerAgentRunStatus empty = new DesignerAgentRunStatus();
            empty.setTargetProcessId(targetProcessId);
            empty.setActive(false);
            return empty;
        }
        return toStatus(run);
    }

    public DesignerAgentRunStatus confirmPlan(String runId, boolean confirmed, String editedPlanJson) {
        DesignerAgentRun run = requireRun(runId);
        if (!AgentRunStage.AwaitPlan.equals(run.getStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在 plan 阶段: " + run.getStage());
        }
        pendingContinuations.put(runId, () -> orchestrator.processPlanConfirmation(run, confirmed, editedPlanJson));
        put(run);
        executeAsync(runId);
        return toStatus(run);
    }

    public DesignerAgentRunStatus confirmPreview(String runId, boolean confirmed) {
        DesignerAgentRun run = requireRun(runId);
        if (!AgentRunStage.AwaitPreview.equals(run.getStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在 preview 阶段: " + run.getStage());
        }
        orchestrator.confirmPreview(run, confirmed);
        if (Boolean.TRUE.equals(confirmed) && StringUtils.isNotBlank(run.getCandidateXml())) {
            saveToProcess(run);
        }
        put(run);
        return toStatus(run);
    }

    public DesignerAgentRunStatus answerAsk(String runId, String userAnswer) {
        DesignerAgentRun run = requireRun(runId);
        if (!AgentRunStage.AwaitAsk.equals(run.getStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在 ask 阶段: " + run.getStage());
        }
        if (StringUtils.isBlank(userAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userAnswer 不能为空");
        }
        orchestrator.prepareAfterAsk(run, userAnswer.trim());
        pendingContinuations.put(runId, () -> orchestrator.runTurn(run));
        put(run);
        executeAsync(runId);
        return toStatus(run);
    }

    private void saveToProcess(DesignerAgentRun run) {
        BpmProcess process = processDao.findById(run.getTargetProcessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程不存在"));
        process.setBpmnXml(run.getCandidateXml());
        processDefinitionService.syncBpmnIdentity(process);
        process.setUpdatedTime(new Date());
        processDao.save(process);
    }

    private DesignerAgentRun requireRun(String runId) {
        DesignerAgentRun run = runsById.get(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "run 不存在: " + runId);
        }
        return run;
    }

    private void put(DesignerAgentRun run) {
        if (AgentRunStage.Done.equals(run.getStage()) || AgentRunStage.Error.equals(run.getStage())) {
            run.setActive(false);
            clearByTarget(run.getTargetProcessId());
            runsById.remove(run.getRunId());
            sinksByRunId.remove(run.getRunId());
            return;
        }
        runsById.put(run.getRunId(), run);
        runsByTarget.put(run.getTargetProcessId(), run);
    }

    private void clearByTarget(String targetProcessId) {
        DesignerAgentRun old = runsByTarget.remove(targetProcessId);
        if (old != null) {
            runsById.remove(old.getRunId());
            sinksByRunId.remove(old.getRunId());
            pendingContinuations.remove(old.getRunId());
        }
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
