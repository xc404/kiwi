package com.kiwi.project.ai.authoring;

import com.kiwi.framework.session.SessionService;
import com.kiwi.project.ai.AiChatProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAuthoringProcessService {

    private final ProcessEngine processEngine;
    private final AiChatProperties aiChatProperties;
    private final SessionService sessionService;

    public boolean isEnabled() {
        return aiChatProperties.getWorkflowAuthoring().isEnabled();
    }

    public StartResult start(String scenario, String targetProcessId, String selectedElementId) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI workflow authoring 未启用");
        }
        if (StringUtils.isBlank(scenario) || StringUtils.isBlank(targetProcessId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario 与 targetProcessId 不能为空");
        }
        String userId = sessionService.getCurrentUser().getId();
        Map<String, Object> vars = new HashMap<>();
        vars.put(AiAuthoringVariables.Scenario, scenario);
        vars.put(AiAuthoringVariables.TargetProcessId, targetProcessId);
        vars.put(AiAuthoringVariables.SelectedElementId, selectedElementId);
        vars.put(AiAuthoringVariables.InitiatorUserId, userId);
        vars.put(AiAuthoringVariables.RepairRound, 0);
        vars.put(AiAuthoringVariables.Stage, AiAuthoringVariables.StageExtract);
        String key = aiChatProperties.getWorkflowAuthoring().getProcessDefinitionKey();
        ProcessInstance instance = processEngine.getRuntimeService()
                .createProcessInstanceByKey(key)
                .businessKey(targetProcessId)
                .setVariables(vars)
                .execute();
        StartResult result = new StartResult();
        result.setProcessInstanceId(instance.getId());
        result.setBusinessKey(targetProcessId);
        fillStatus(result, instance.getId());
        return result;
    }

    public StatusResult status(String processInstanceId) {
        StatusResult result = new StatusResult();
        result.setProcessInstanceId(processInstanceId);
        fillStatus(result, processInstanceId);
        return result;
    }

    public StatusResult statusByTargetProcess(String targetProcessId) {
        ProcessInstance pi = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(aiChatProperties.getWorkflowAuthoring().getProcessDefinitionKey())
                .processInstanceBusinessKey(targetProcessId)
                .active()
                .singleResult();
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
        result.setStage(stringVar(vars, AiAuthoringVariables.Stage));
        result.setDispatchCode(stringVar(vars, AiAuthoringVariables.DispatchCode));
        result.setCandidateXml(stringVar(vars, AiAuthoringVariables.CandidateXml));
        result.setAskMessage(stringVar(vars, AiAuthoringVariables.AskMessage));
        result.setPluginHintJson(stringVar(vars, AiAuthoringVariables.PluginHintJson));
        result.setIssuesJson(stringVar(vars, AiAuthoringVariables.IssuesJson));
        result.setCatalogJson(stringVar(vars, AiAuthoringVariables.CatalogJson));
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
