package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.BpmActivityPointerDto;
import com.kiwi.project.bpm.dto.BpmInstanceRecoverResultDto;
import com.kiwi.project.bpm.dto.BpmOpenIncidentDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceDto;
import com.kiwi.project.bpm.dto.ProcessInstanceState;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
public class BpmProcessInstanceCtl extends BaseCtl {

    private final ProcessEngine processEngine;

    /**
     * @param unfinished 已弃用，请改用 {@code instanceState}；若传入则优先于 {@code instanceState}（true→运行中，false→全部）
     */
    @GetMapping
    @ResponseBody
    public Page<BpmProcessInstanceDto> page(
            @RequestParam(required = false) String processDefinitionKey,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) Boolean unfinished,
            @RequestParam(defaultValue = "running") String instanceState,
            Pageable pageable) {

        HistoryService historyService = processEngine.getHistoryService();
        InstanceState state = resolveInstanceState(unfinished, instanceState);
        HistoricProcessInstanceQuery query = buildQuery(
                historyService, processDefinitionKey, businessKey, processInstanceId, state);

        long total = query.count();
        List<HistoricProcessInstance> list = query
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((int) pageable.getOffset(), pageable.getPageSize());

        List<BpmProcessInstanceDto> content = list.stream()
                .map(this::toRow)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 供 cryoEMS 等查询流程实例。
     */
    @GetMapping("{instanceId}")
    public BpmProcessInstanceDto get(@PathVariable String instanceId) {
        HistoryService historyService = processEngine.getHistoryService();
        RuntimeService runtimeService = processEngine.getRuntimeService();
        RepositoryService repositoryService = processEngine.getRepositoryService();

        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (pi != null) {
            BpmProcessInstanceDto dto = new BpmProcessInstanceDto();
            dto.setId(pi.getId());
            dto.setEnded(false);
            dto.setSuspended(pi.isSuspended());
            List<Incident> openIncidents =
                    runtimeService.createIncidentQuery().processInstanceId(instanceId).list();
            dto.setOpenIncidents(mapOpenIncidents(openIncidents, repositoryService));
            fillCurrentActivities(
                    historyService, repositoryService, runtimeService, instanceId, pi.getProcessDefinitionId(), dto);
            if (!openIncidents.isEmpty()) {
                dto.setState(ProcessInstanceState.ERROR);
            } else {
                dto.setState(pi.isSuspended() ? ProcessInstanceState.SUSPENDED : ProcessInstanceState.RUNNING);
            }
            return dto;
        }

        HistoricProcessInstance hip = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (hip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found");
        }

        BpmProcessInstanceDto dto = new BpmProcessInstanceDto();
        dto.setId(hip.getId());
        dto.setEndTime(hip.getEndTime());
        dto.setDeleteReason(hip.getDeleteReason());
        if (hip.getEndTime() == null) {
            dto.setEnded(false);
            dto.setState(ProcessInstanceState.ACTIVE);
            fillCurrentActivities(
                    historyService, repositoryService, runtimeService, instanceId, hip.getProcessDefinitionId(), dto);
            dto.setOpenIncidents(new ArrayList<>());
            return dto;
        }
        dto.setEnded(true);
        boolean canceled = StringUtils.hasText(hip.getDeleteReason());
        dto.setState(canceled ? ProcessInstanceState.CANCELED : ProcessInstanceState.COMPLETED);
        return dto;
    }

    /**
     * 对运行中实例上所有 OPEN 的 incident 做一键恢复：为 {@code failedJob} / {@code failedJobListener} 重置 Job retries，
     * 为 {@code failedExternalTask} 重置 External Task retries。其它类型仅计入 skipped，不抛错。
     * <p>实例若处于挂起，仍会写入 retries，但需激活后才会被拉取执行。
     *
     * @param retries 写入的重试次数，默认 3，范围 1～100
     */
    @PostMapping("{instanceId}/recover")
    public BpmInstanceRecoverResultDto recover(
            @PathVariable String instanceId,
            @RequestParam(defaultValue = "3") int retries) {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (pi == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "running process instance not found");
        }

        int retriesApplied = Math.min(100, Math.max(1, retries));
        List<Incident> incidents =
                runtimeService.createIncidentQuery().processInstanceId(instanceId).list();

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

    private HistoricProcessInstanceQuery buildQuery(
            HistoryService historyService,
            String processDefinitionKey,
            String businessKey,
            String processInstanceId,
            InstanceState state) {

        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();
        switch (state) {
            case RUNNING -> query.unfinished();
            case COMPLETED -> query.completed();
            case ALL -> {}
        }

        if (isNotBlank(processDefinitionKey)) {
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

    private BpmProcessInstanceDto toRow(HistoricProcessInstance pi) {
        BpmProcessInstanceDto row = new BpmProcessInstanceDto();
        row.setId(pi.getId());
        row.setBusinessKey(pi.getBusinessKey());
        row.setProcessDefinitionId(pi.getProcessDefinitionId());
        row.setProcessDefinitionKey(pi.getProcessDefinitionKey());
        row.setProcessDefinitionName(pi.getProcessDefinitionName());
        row.setStartTime(pi.getStartTime());
        row.setTenantId(pi.getTenantId());
        return row;
    }

    private static List<BpmOpenIncidentDto> mapOpenIncidents(
            List<Incident> incidents, RepositoryService repositoryService) {
        List<BpmOpenIncidentDto> out = new ArrayList<>(incidents.size());
        for (Incident i : incidents) {
            BpmOpenIncidentDto row = new BpmOpenIncidentDto();
            row.setIncidentId(i.getId());
            row.setIncidentType(i.getIncidentType());
            row.setMessage(i.getIncidentMessage());
            row.setActivityId(i.getActivityId());
            row.setActivityName(
                    resolveActivityName(repositoryService, i.getProcessDefinitionId(), i.getActivityId()));
            out.add(row);
        }
        return out;
    }

    private static void fillCurrentActivities(
            HistoryService historyService,
            RepositoryService repositoryService,
            RuntimeService runtimeService,
            String processInstanceId,
            String processDefinitionId,
            BpmProcessInstanceDto dto) {

        List<HistoricActivityInstance> historic = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .unfinished()
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        List<BpmActivityPointerDto> pointers = new ArrayList<>();
        if (!historic.isEmpty()) {
            for (HistoricActivityInstance h : historic) {
                BpmActivityPointerDto p = new BpmActivityPointerDto();
                p.setActivityId(h.getActivityId());
                p.setActivityType(h.getActivityType());
                String name = StringUtils.hasText(h.getActivityName()) ? h.getActivityName() : null;
                if (!StringUtils.hasText(name) && StringUtils.hasText(h.getActivityId())) {
                    name = resolveActivityName(repositoryService, processDefinitionId, h.getActivityId());
                }
                p.setActivityName(name);
                pointers.add(p);
            }
        } else if (StringUtils.hasText(processDefinitionId)) {
            for (String aid : runtimeService.getActiveActivityIds(processInstanceId)) {
                if (!StringUtils.hasText(aid)) {
                    continue;
                }
                BpmActivityPointerDto p = new BpmActivityPointerDto();
                p.setActivityId(aid);
                p.setActivityName(resolveActivityName(repositoryService, processDefinitionId, aid));
                pointers.add(p);
            }
        }
        dto.setCurrentActivities(pointers);
    }

    private static String resolveActivityName(
            RepositoryService repositoryService, String processDefinitionId, String activityId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        try {
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);
            ModelElementInstance el = model.getModelElementById(activityId);
            if (el instanceof FlowNode) {
                String n = ((FlowNode) el).getName();
                return StringUtils.hasText(n) ? n : null;
            }
        } catch (RuntimeException ignored) {
            // 定义缺失或模型不可解析时忽略
        }
        return null;
    }
}
