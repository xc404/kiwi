package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.BpmHistoricActivityInstanceDto;
import com.kiwi.project.bpm.dto.BpmHistoricVariableInstanceDto;
import com.kiwi.project.bpm.dto.BpmInstanceRecoverResultDto;
import com.kiwi.project.bpm.dto.BpmProcessDefinitionXmlDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceBatchIdsRequest;
import com.kiwi.project.bpm.dto.BpmProcessInstanceDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceStateDto;
import com.kiwi.project.bpm.service.BpmOwnershipAccessService;
import com.kiwi.project.bpm.service.BpmProcessInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.ExternalTaskService;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.history.HistoricProcessInstanceQuery;
import org.operaton.bpm.engine.runtime.Incident;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程实例：分页列表、实例状态查询，挂载 {@code /bpm/process-instance}；启动实例见 {@link BpmProcessDefinitionCtl} {@code POST /bpm/process/{id}/start}。
 * <p>列表主参数 {@code instanceState}：{@code running}（默认）| {@code completed} | {@code all}。
 * 弃用参数 {@code unfinished}：{@code true}→运行中，{@code false}→全部（与旧版兼容）。
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/bpm/process-instance")
@Tag(name = "BPM 流程实例", description = "实例分页、状态查询与 incident 恢复")
public class BpmProcessInstanceCtl extends BaseCtl {

    private final ProcessEngine processEngine;
    private final BpmProcessInstanceService bpmProcessInstanceService;
    private final BpmOwnershipAccessService bpmOwnershipAccessService;

    private HistoryService historyService() {
        return processEngine.getHistoryService();
    }

    private RuntimeService runtimeService() {
        return processEngine.getRuntimeService();
    }

    private TaskService taskService() {
        return processEngine.getTaskService();
    }

    /** 完成 UserTask / 推动 ManualTask 时透传的流程变量（可选）。 */
    @Data
    public static class CompleteTaskInput {
        private Map<String, Object> variables;
    }

    /**
     * @param unfinished 已弃用，请改用 {@code instanceState}；若传入则优先于 {@code instanceState}（true→运行中，false→全部）
     */
    @Operation(
            operationId = "bpmInst_page",
            summary = "分页查询流程实例",
            description = "instanceState：running（默认）| completed | all；unfinished 已弃用。")
    @GetMapping
    @ResponseBody
    public Page<BpmProcessInstanceDto> page(
            @RequestParam(required = false) String processDefinitionKey,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) Boolean unfinished,
            @RequestParam(defaultValue = "running") String instanceState,
            Pageable pageable) {

        InstanceState state = resolveInstanceState(unfinished, instanceState);
        List<String> ownedKeys = resolveOwnedProcessKeysForPage(processDefinitionKey);
        if (ownedKeys != null && ownedKeys.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        HistoricProcessInstanceQuery query =
                buildQuery(processDefinitionKey, businessKey, processInstanceId, state, ownedKeys);

        long total = query.count();
        List<HistoricProcessInstance> list = query
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((int) pageable.getOffset(), pageable.getPageSize());

        List<BpmProcessInstanceDto> content = list.stream()
                .map(bpmProcessInstanceService::toListRowDto)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 供 cryoEMS 等查询流程实例。
     */
    @Operation(operationId = "bpmInst_get", summary = "按实例 id 获取流程实例详情")
    @GetMapping("{instanceId}")
    public BpmProcessInstanceDto get(@PathVariable String instanceId) {
        bpmOwnershipAccessService.assertOwnsInstance(getCurrentUserId(), instanceId);

        HistoricProcessInstance hip = historyService().createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (hip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found");
        }

        return bpmProcessInstanceService.detailDtoFromHistoric(instanceId, hip);
    }

    @Operation(operationId = "bpmInst_definitionXml", summary = "获取流程实例对应定义的 BPMN XML")
    @GetMapping("{instanceId}/definition-xml")
    @ResponseBody
    public BpmProcessDefinitionXmlDto definitionXml(@PathVariable String instanceId) {
        bpmOwnershipAccessService.assertOwnsInstance(getCurrentUserId(), instanceId);
        return bpmProcessInstanceService.definitionXmlForInstance(instanceId);
    }

    @Operation(operationId = "bpmInst_historyActivities", summary = "获取流程实例历史活动（画布高亮）")
    @GetMapping("{instanceId}/history-activities")
    @ResponseBody
    public List<BpmHistoricActivityInstanceDto> historyActivities(@PathVariable String instanceId) {
        bpmOwnershipAccessService.assertOwnsInstance(getCurrentUserId(), instanceId);
        return bpmProcessInstanceService.historyActivitiesForInstance(instanceId);
    }

    @Operation(operationId = "bpmInst_variables", summary = "获取流程实例历史变量列表")
    @GetMapping("{instanceId}/variables")
    @ResponseBody
    public List<BpmHistoricVariableInstanceDto> variables(@PathVariable String instanceId) {
        bpmOwnershipAccessService.assertOwnsInstance(getCurrentUserId(), instanceId);
        return bpmProcessInstanceService.historicVariablesForInstance(instanceId);
    }

    /**
     * 轻量状态（机机轮询）：含 incident 导致的 ERROR 时返回 {@code errorReason} 与错误节点字段。
     */
    @Operation(operationId = "bpmInst_getState", summary = "获取流程实例轻量状态（供轮询）")
    @GetMapping("{instanceId}/state")
    @ResponseBody
    public BpmProcessInstanceStateDto getState(@PathVariable String instanceId) {
        return bpmProcessInstanceService.findStateDto(instanceId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found"));
    }

    /**
     * 批量查询流程实例状态；顺序与请求体 {@code instanceIds} 一致（服务端去重，上限 {@link BpmProcessInstanceService#MAX_INSTANCE_IDS_PER_BATCH}）。
     */
    @Operation(operationId = "bpmInst_batchStates", summary = "批量查询流程实例状态")
    @PostMapping("states")
    @ResponseBody
    public List<BpmProcessInstanceStateDto> batchStates(
            @RequestBody(required = false) BpmProcessInstanceBatchIdsRequest body) {
        if (body == null || body.getInstanceIds() == null) {
            return List.of();
        }
        return bpmProcessInstanceService.batchStateDtosInRequestOrder(body.getInstanceIds());
    }

    /**
     * 对运行中实例上所有 OPEN 的 incident 做一键恢复：为 {@code failedJob} / {@code failedJobListener} 重置 Job retries，
     * 为 {@code failedExternalTask} 重置 External Task retries。其它类型仅计入 skipped，不抛错。
     * <p>实例若处于挂起，仍会写入 retries，但需激活后才会被拉取执行。
     *
     * @param retries 写入的重试次数，默认 3，范围 1～100
     */
    @Operation(
            operationId = "bpmInst_recover",
            summary = "对运行中实例的 OPEN incident 一键恢复",
            description = "retries 默认 3，范围 1～100。")
    @PostMapping("{instanceId}/recover")
    public BpmInstanceRecoverResultDto recover(
            @PathVariable String instanceId,
            @RequestParam(defaultValue = "3") int retries) {
        bpmOwnershipAccessService.assertOwnsInstance(getCurrentUserId(), instanceId);
        ProcessInstance pi = this.runtimeService().createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (pi == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "running process instance not found");
        }

        int retriesApplied = Math.min(100, Math.max(1, retries));
        List<Incident> incidents =
                runtimeService().createIncidentQuery().processInstanceId(instanceId).list();

        ManagementService managementService = processEngine.getManagementService();
        ExternalTaskService externalTaskService = processEngine.getExternalTaskService();

        Set<String> jobIdsDone = new HashSet<>();
        Set<String> extTaskIdsDone = new HashSet<>();
        int jobsRetried = 0;
        int externalTasksRetried = 0;
        int skipped = 0;

        for (Incident inc : incidents) {
            String type = inc.getIncidentType();
            String cfg = inc.getConfiguration();
            if (!StringUtils.hasText(cfg)) {
                skipped++;
                continue;
            }
            if ("failedJob".equals(type) || "failedJobListener".equals(type)) {
                if (jobIdsDone.add(cfg)) {
                    managementService.setJobRetries(cfg, retriesApplied);
                    jobsRetried++;
                }
            } else if ("failedExternalTask".equals(type)) {
                if (extTaskIdsDone.add(cfg)) {
                    externalTaskService.setRetries(cfg, retriesApplied);
                    externalTasksRetried++;
                }
            } else {
                skipped++;
            }
        }

        BpmInstanceRecoverResultDto out = new BpmInstanceRecoverResultDto();
        out.setOpenIncidentCount(incidents.size());
        out.setJobsRetried(jobsRetried);
        out.setExternalTasksRetried(externalTasksRetried);
        out.setIncidentsSkipped(skipped);
        out.setRetriesApplied(retriesApplied);
        return out;
    }

    /**
     * 按 {@code instanceId} 与 {@code taskKey} 定位唯一等待节点并推进流程：
     * <ol>
     *   <li>优先按 {@code taskDefinitionKey} 查 active UserTask（{@link TaskService#complete(String, Map)}）；</li>
     *   <li>未命中则按 {@code activityId} 查 async-continuation Job（覆盖 ManualTask {@code asyncBefore="true"}
     *       等以 Job 形式停泊的等待节点）：先写入变量再 {@link ManagementService#executeJob(String)}。</li>
     * </ol>
     * <p>典型用例：cryoEMS 等外部系统在满足业务前置条件后，主动完成 BPMN 中作为"等待"节点的
     * UserTask / ManualTask 推动流程。
     *
     * @return 推动成功后查询到的轻量状态；失败按 HTTP 语义：404=实例不存在/已结束、409=无匹配或多条匹配。
     */
    @Operation(
            operationId = "bpmInst_completeTask",
            summary = "按 instance + taskKey 推动等待节点（UserTask 或 ManualTask asyncBefore Job）",
            description = "供机机集成（如 cryoEMS）推动 BPMN 等待节点；404=实例不存在/已结束；409=无匹配或多条匹配。")
    @PostMapping("{instanceId}/tasks/{taskKey}/complete")
    @ResponseBody
    public BpmProcessInstanceStateDto complete(
            @PathVariable String instanceId,
            @PathVariable String taskKey,
            @RequestBody(required = false) CompleteTaskInput body) {

        if (!StringUtils.hasText(instanceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instanceId is required");
        }
        if (!StringUtils.hasText(taskKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskKey is required");
        }

        String pid = instanceId.trim();
        String key = taskKey.trim();

        ProcessInstance pi = runtimeService().createProcessInstanceQuery()
                .processInstanceId(pid)
                .singleResult();
        if (pi == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "process instance not found or already ended: " + instanceId);
        }

        Map<String, Object> variables = body != null && body.getVariables() != null
                ? body.getVariables()
                : Map.of();

        // 1) UserTask 路径：taskService.complete
        List<Task> userTasks = taskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(key)
                .active()
                .list();
        if (userTasks.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ambiguous: multiple active user tasks with key '" + taskKey + "' on instance '" + instanceId + "'");
        }
        if (userTasks.size() == 1) {
            taskService().complete(userTasks.get(0).getId(), variables);
            return loadStateAfterComplete(pid);
        }

        // 2) ManualTask / asyncBefore-Job 路径：managementService.executeJob
        ManagementService managementService = processEngine.getManagementService();
        List<Job> jobs = managementService.createJobQuery()
                .processInstanceId(pid)
                .activityId(key)
                .list();
        if (jobs.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ambiguous: multiple async-continuation jobs with activity id '" + taskKey
                            + "' on instance '" + instanceId + "'");
        }
        if (jobs.size() == 1) {
            Job job = jobs.get(0);
            if (!variables.isEmpty() && StringUtils.hasText(job.getExecutionId())) {
                runtimeService().setVariables(job.getExecutionId(), variables);
            }
            managementService.executeJob(job.getId());
            return loadStateAfterComplete(pid);
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "no active user task and no async-continuation job with key '" + taskKey
                        + "' on instance '" + instanceId + "'");
    }

    private BpmProcessInstanceStateDto loadStateAfterComplete(String processInstanceId) {
        return bpmProcessInstanceService.findStateDto(processInstanceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "process instance state unavailable after complete: " + processInstanceId));
    }

    private static InstanceState resolveInstanceState(Boolean unfinishedLegacy, String instanceStateParam) {
        if (unfinishedLegacy != null) {
            return unfinishedLegacy ? InstanceState.RUNNING : InstanceState.ALL;
        }
        return InstanceState.fromApi(instanceStateParam);
    }

    private enum InstanceState {
        RUNNING,
        COMPLETED,
        ALL;

        static InstanceState fromApi(String raw) {
            if (raw == null || raw.isBlank()) {
                return RUNNING;
            }
            switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "running":
                    return RUNNING;
                case "completed":
                    return COMPLETED;
                case "all":
                    return ALL;
                default:
                    return RUNNING;
            }
        }
    }

    /**
     * 非管理员：返回本人流程 key 列表；若请求指定了他人流程 key 则返回空列表。
     * 管理员：返回 {@code null} 表示不限制。
     */
    private List<String> resolveOwnedProcessKeysForPage(String processDefinitionKey) {
        if (bpmOwnershipAccessService.isBpmAdmin()) {
            return null;
        }
        List<String> owned = bpmOwnershipAccessService.ownedProcessKeys(getCurrentUserId());
        if (isNotBlank(processDefinitionKey)) {
            String key = processDefinitionKey.trim();
            return owned.contains(key) ? List.of(key) : List.of();
        }
        return owned;
    }

    private HistoricProcessInstanceQuery buildQuery(
            String processDefinitionKey,
            String businessKey,
            String processInstanceId,
            InstanceState state,
            List<String> ownedProcessKeys) {

        HistoricProcessInstanceQuery query = historyService().createHistoricProcessInstanceQuery();
        switch (state) {
            case RUNNING -> query.unfinished();
            case COMPLETED -> query.completed();
            case ALL -> {}
        }

        if (ownedProcessKeys != null) {
            if (ownedProcessKeys.size() == 1) {
                query.processDefinitionKey(ownedProcessKeys.get(0));
            } else {
                query.processDefinitionKeyIn(ownedProcessKeys.toArray(String[]::new));
            }
        } else if (isNotBlank(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey.trim());
        }
        if (isNotBlank(businessKey)) {
            query.processInstanceBusinessKey(businessKey.trim());
        }
        if (isNotBlank(processInstanceId)) {
            query.processInstanceId(processInstanceId.trim());
        }
        return query;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
