package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.BpmInstanceRecoverResultDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceDto;
import com.kiwi.project.bpm.service.BpmProcessInstanceService;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
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
@RequestMapping("/bpm/process-instance")
public class BpmProcessInstanceCtl extends BaseCtl {

    private final ProcessEngine processEngine;
    private final HistoryService historyService;
    private final BpmProcessInstanceService bpmProcessInstanceService;
    private final RuntimeService runtimeService;

    public BpmProcessInstanceCtl(ProcessEngine processEngine, BpmProcessInstanceService bpmProcessInstanceService) {
        this.processEngine = processEngine;
        this.historyService = processEngine.getHistoryService();
        this.bpmProcessInstanceService = bpmProcessInstanceService;
        this.runtimeService = processEngine.getRuntimeService();
    }

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

        InstanceState state = resolveInstanceState(unfinished, instanceState);
        HistoricProcessInstanceQuery query = buildQuery(processDefinitionKey, businessKey, processInstanceId, state);

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
    @GetMapping("{instanceId}")
    public BpmProcessInstanceDto get(@PathVariable String instanceId) {

        HistoricProcessInstance hip = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (hip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found");
        }

        return bpmProcessInstanceService.detailDtoFromHistoric(instanceId, hip);
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
        ProcessInstance pi = this.runtimeService.createProcessInstanceQuery()
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
}
