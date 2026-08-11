package com.kiwi.bpmn.assistant;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.Incident;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssistantProcessService {

    private final ProcessEngine processEngine;
    private final AssistantProperties assistantProperties;

    public boolean isEnabled() {
        return assistantProperties.isEnabled();
    }

    public StartResult start(
            String scenario,
            String targetProcessId,
            String selectedElementId,
            String baseBpmnXml,
            String initiatorUserId) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI workflow authoring 未启用");
        }
        if (StringUtils.isBlank(scenario) || StringUtils.isBlank(targetProcessId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario 与 targetProcessId 不能为空");
        }
        cancelActiveAuthoringInstances(targetProcessId);

        Map<String, Object> vars = new HashMap<>();
        vars.put(AssistantVariables.Scenario, scenario);
        vars.put(AssistantVariables.TargetProcessId, targetProcessId);
        vars.put(AssistantVariables.SelectedElementId, selectedElementId);
        vars.put(AssistantVariables.InitiatorUserId, initiatorUserId);
        vars.put(AssistantVariables.RepairRound, 0);
        if (StringUtils.isNotBlank(baseBpmnXml)) {
            String xml = baseBpmnXml.trim();
            vars.put(AssistantVariables.BaseBpmnXml, xml);
            vars.put(AssistantVariables.CandidateXml, xml);
        }
        String key = assistantProperties.getProcessDefinitionKey();
        ProcessInstance instance = processEngine.getRuntimeService()
                .createProcessInstanceByKey(key)
                .businessKey(targetProcessId)
                .setVariables(vars)
                .execute();
        waitForAuthoringWaitState(instance.getId());
        StartResult result = new StartResult();
        result.setProcessInstanceId(instance.getId());
        result.setBusinessKey(targetProcessId);
        fillStatus(result, instance.getId());
        assertAuthoringAdvanced(result);
        return result;
    }

    public StatusResult status(String processInstanceId) {
        StatusResult result = new StatusResult();
        result.setProcessInstanceId(processInstanceId);
        fillStatus(result, processInstanceId);
        return result;
    }

    public StatusResult statusByTargetProcess(String targetProcessId) {
        ProcessInstance pi = findLatestActiveAuthoringInstance(targetProcessId);
        if (pi == null) {
            StatusResult empty = new StatusResult();
            empty.setTargetProcessId(targetProcessId);
            empty.setActive(false);
            return empty;
        }
        return status(pi.getId());
    }

    public StatusResult completeTask(String taskId, Map<String, Object> variables) {
        Task task = processEngine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在: " + taskId);
        }
        Map<String, Object> vars = variables != null ? new HashMap<>(variables) : new HashMap<>();
        processEngine.getTaskService().complete(taskId, vars);
        return status(task.getProcessInstanceId());
    }

    private void cancelActiveAuthoringInstances(String targetProcessId) {
        List<ProcessInstance> existing = listActiveAuthoringInstances(targetProcessId);
        for (ProcessInstance pi : existing) {
            processEngine.getRuntimeService()
                    .deleteProcessInstance(pi.getId(), "superseded by new AI authoring run");
        }
    }

    private ProcessInstance findLatestActiveAuthoringInstance(String targetProcessId) {
        String key = assistantProperties.getProcessDefinitionKey();
        List<HistoricProcessInstance> latest =
                processEngine.getHistoryService().createHistoricProcessInstanceQuery()
                        .processDefinitionKey(key)
                        .processInstanceBusinessKey(targetProcessId)
                        .unfinished()
                        .orderByProcessInstanceStartTime()
                        .desc()
                        .listPage(0, 1);
        if (latest.isEmpty()) {
            return null;
        }
        return processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(latest.getFirst().getId())
                .active()
                .singleResult();
    }

    private List<ProcessInstance> listActiveAuthoringInstances(String targetProcessId) {
        return processEngine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(assistantProperties.getProcessDefinitionKey())
                .processInstanceBusinessKey(targetProcessId)
                .active()
                .list();
    }

    private void waitForAuthoringWaitState(String processInstanceId) {
        long deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            throwIfIncidents(processInstanceId);
            ProcessInstance pi = processEngine.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (pi == null) {
                return;
            }
            long taskCount = processEngine.getTaskService().createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .count();
            if (taskCount > 0) {
                return;
            }
            String stage = readStage(processInstanceId);
            if (AssistantVariables.StageAwaitPreview.equals(stage)
                    || AssistantVariables.StageAwaitInstall.equals(stage)
                    || AssistantVariables.StageAwaitAsk.equals(stage)
                    || AssistantVariables.StageDone.equals(stage)) {
                return;
            }
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "等待 AI 写工作流编排被中断");
            }
        }
        throwIfIncidents(processInstanceId);
    }

    private String readStage(String processInstanceId) {
        try {
            Object v = processEngine.getRuntimeService().getVariable(processInstanceId, AssistantVariables.Stage);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private void throwIfIncidents(String processInstanceId) {
        List<Incident> incidents = processEngine.getRuntimeService().createIncidentQuery()
                .processInstanceId(processInstanceId)
                .list();
        if (incidents.isEmpty()) {
            return;
        }
        String detail = incidents.stream()
                .map(i -> i.getActivityId() + ": " + i.getIncidentMessage())
                .collect(Collectors.joining("; "));
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "AI 写工作流编排失败: " + detail);
    }

    private void assertAuthoringAdvanced(StartResult result) {
        if (!result.isActive()) {
            return;
        }
        boolean hasTasks = result.getTasks() != null && !result.getTasks().isEmpty();
        String stage = result.getStage();
        boolean waiting = AssistantVariables.StageAwaitPreview.equals(stage)
                || AssistantVariables.StageAwaitInstall.equals(stage)
                || AssistantVariables.StageAwaitAsk.equals(stage);
        if (hasTasks || waiting) {
            return;
        }
        throwIfIncidents(result.getProcessInstanceId());
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "AI 写工作流编排未进入人机确认阶段（当前 stage="
                        + stage + "）。请检查编排 BPMN 是否已重新部署，以及后端日志中的 delegate 异常。");
    }

    private void fillStatus(StatusResult result, String processInstanceId) {
        ProcessInstance pi = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        result.setActive(pi != null && !pi.isEnded());
        if (pi != null) {
            result.setBusinessKey(pi.getBusinessKey());
            result.setTargetProcessId(pi.getBusinessKey());
        }
        Map<String, Object> vars = Map.of();
        try {
            vars = processEngine.getRuntimeService().getVariables(processInstanceId);
            if (vars == null) {
                vars = Map.of();
            }
        } catch (Exception ignored) {
            // ended instance may have no runtime variables
        }
        result.setStage(stringVar(vars, AssistantVariables.Stage));
        result.setDispatchCode(stringVar(vars, AssistantVariables.DispatchCode));
        result.setCandidateXml(stringVar(vars, AssistantVariables.CandidateXml));
        result.setAssistantReply(stringVar(vars, AssistantVariables.AssistantReply));
        result.setAskMessage(stringVar(vars, AssistantVariables.AskMessage));
        result.setPluginHintJson(stringVar(vars, AssistantVariables.PluginHintJson));
        result.setIssuesJson(stringVar(vars, AssistantVariables.IssuesJson));
        result.setCatalogJson(stringVar(vars, AssistantVariables.CatalogJson));
        result.setVariables(new LinkedHashMap<>(vars));

        List<Task> tasks = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        result.setTasks(tasks.stream().map(t -> {
            TaskInfo info = new TaskInfo();
            info.setId(t.getId());
            info.setName(t.getName());
            info.setTaskDefinitionKey(t.getTaskDefinitionKey());
            info.setAssignee(t.getAssignee());
            return info;
        }).toList());
    }

    private static String stringVar(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @Data
    public static class StartResult extends StatusResult {
    }

    @Data
    public static class StatusResult {
        private String processInstanceId;
        private String businessKey;
        private String targetProcessId;
        private boolean active;
        private String stage;
        private String dispatchCode;
        private String candidateXml;
        private String assistantReply;
        private String askMessage;
        private String pluginHintJson;
        private String issuesJson;
        private String catalogJson;
        private List<TaskInfo> tasks;
        private Map<String, Object> variables;
    }

    @Data
    public static class TaskInfo {
        private String id;
        private String name;
        private String taskDefinitionKey;
        private String assignee;
    }
}
