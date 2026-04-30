package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.ProcessInstanceStateDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    public Page<BpmProcessInstanceRow> page(
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

        List<BpmProcessInstanceRow> content = list.stream()
                .map(this::toRow)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 供 cryoEMS 等查询流程实例状态（运行中 / 历史）。
     */
    @GetMapping("{instanceId}/state")
    public ProcessInstanceStateDto state(@PathVariable String instanceId) {
        HistoryService historyService = processEngine.getHistoryService();
        ProcessInstance pi = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (pi != null) {
            ProcessInstanceStateDto dto = new ProcessInstanceStateDto();
            dto.setId(pi.getId());
            dto.setEnded(false);
            dto.setSuspended(pi.isSuspended());
            dto.setState(pi.isSuspended() ? "SUSPENDED" : "RUNNING");
            return dto;
        }

        HistoricProcessInstance hip = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (hip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found");
        }

        ProcessInstanceStateDto dto = new ProcessInstanceStateDto();
        dto.setId(hip.getId());
        dto.setEndTime(hip.getEndTime());
        dto.setDeleteReason(hip.getDeleteReason());
        if (hip.getEndTime() == null) {
            dto.setEnded(false);
            dto.setState("ACTIVE");
            return dto;
        }
        dto.setEnded(true);
        boolean canceled = StringUtils.hasText(hip.getDeleteReason());
        dto.setState(canceled ? "CANCELED" : "COMPLETED");
        return dto;
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

    private BpmProcessInstanceRow toRow(HistoricProcessInstance pi) {
        BpmProcessInstanceRow row = new BpmProcessInstanceRow();
        row.setId(pi.getId());
        row.setBusinessKey(pi.getBusinessKey());
        row.setProcessDefinitionId(pi.getProcessDefinitionId());
        row.setProcessDefinitionKey(pi.getProcessDefinitionKey());
        row.setProcessDefinitionName(pi.getProcessDefinitionName());
        row.setStartTime(pi.getStartTime());
        row.setTenantId(pi.getTenantId());
        return row;
    }

    @Data
    public static class BpmProcessInstanceRow {
        private String id;
        private String businessKey;
        private String processDefinitionId;
        private String processDefinitionKey;
        private String processDefinitionName;
        private Date startTime;
        private String tenantId;
    }
}
