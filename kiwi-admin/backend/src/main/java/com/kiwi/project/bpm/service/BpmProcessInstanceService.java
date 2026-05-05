package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dto.BpmActivityPointerDto;
import com.kiwi.project.bpm.dto.BpmOpenIncidentDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceDto;
import com.kiwi.project.bpm.dto.ProcessInstanceState;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class BpmProcessInstanceService {

    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    public BpmProcessInstanceService(ProcessEngine processEngine) {
        this.historyService = processEngine.getHistoryService();
        this.runtimeService = processEngine.getRuntimeService();
        this.repositoryService = processEngine.getRepositoryService();
    }

    /**
     * 历史实例 → 分页列表行（仅列表维度字段）。
     */
    public BpmProcessInstanceDto toListRowDto(HistoricProcessInstance pi) {
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

    /**
     * 历史实例 → 单实例详情：基础字段、运行中时的当前活动与 incident，以及 {@link ProcessInstanceState}。
     *
     * @param processInstanceId Camunda 查询用实例 id（通常与 {@code hip.getId()} 一致）
     */
    public BpmProcessInstanceDto detailDtoFromHistoric(String processInstanceId, HistoricProcessInstance hip) {
        BpmProcessInstanceDto dto = new BpmProcessInstanceDto();
        dto.setId(hip.getId());
        dto.setEndTime(hip.getEndTime());
        dto.setStartTime(hip.getStartTime());
        dto.setProcessDefinitionId(hip.getProcessDefinitionId());
        dto.setProcessDefinitionKey(hip.getProcessDefinitionKey());
        dto.setProcessDefinitionName(hip.getProcessDefinitionName());
        dto.setDeleteReason(hip.getDeleteReason());

        if (hip.getEndTime() == null) {
            dto.setEnded(false);
            BpmnModelInstance bpmnModel = loadBpmnModel(hip.getProcessDefinitionId());
            fillCurrentActivities(processInstanceId, hip.getProcessDefinitionId(), bpmnModel, dto);
            List<Incident> incidents = fillOpenIncidents(processInstanceId, dto, bpmnModel);
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (pi != null) {
                dto.setSuspended(pi.isSuspended());
                if (!incidents.isEmpty()) {
                    dto.setState(ProcessInstanceState.ERROR);
                } else if (pi.isSuspended()) {
                    dto.setState(ProcessInstanceState.SUSPENDED);
                } else {
                    dto.setState(ProcessInstanceState.RUNNING);
                }
            } else {
                dto.setSuspended(false);
                dto.setState(!incidents.isEmpty() ? ProcessInstanceState.ERROR : ProcessInstanceState.ACTIVE);
            }
        }else {

        dto.setEnded(true);
        boolean canceled = StringUtils.hasText(hip.getDeleteReason());
        dto.setState(canceled ? ProcessInstanceState.CANCELED : ProcessInstanceState.COMPLETED);
        }
        return dto;
    }

    /**
     * 运行中实例：填充当前未结束活动指针。
     */
    private void fillCurrentActivities(
            String processInstanceId,
            String processDefinitionId,
            BpmnModelInstance bpmnModel,
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
                    name = resolveActivityName(bpmnModel, h.getActivityId());
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
                p.setActivityName(resolveActivityName(bpmnModel, aid));
                pointers.add(p);
            }
        }
        dto.setCurrentActivities(pointers);
    }

    /**
     * 运行中实例：查询 OPEN incident 并写入 DTO。
     */
    private List<Incident> fillOpenIncidents(
            String processInstanceId, BpmProcessInstanceDto dto, BpmnModelInstance bpmnModel) {
        List<Incident> incidents =
                runtimeService.createIncidentQuery().processInstanceId(processInstanceId).list();
        dto.setOpenIncidents(mapOpenIncidents(incidents, bpmnModel));
        return incidents;
    }

    private List<BpmOpenIncidentDto> mapOpenIncidents(List<Incident> incidents, BpmnModelInstance bpmnModel) {
        List<BpmOpenIncidentDto> out = new ArrayList<>(incidents.size());
        for (Incident i : incidents) {
            BpmOpenIncidentDto row = new BpmOpenIncidentDto();
            row.setIncidentId(i.getId());
            row.setIncidentType(i.getIncidentType());
            row.setMessage(i.getIncidentMessage());
            row.setActivityId(i.getActivityId());
            row.setActivityName(resolveActivityName(bpmnModel, i.getActivityId()));
            out.add(row);
        }
        return out;
    }

    private BpmnModelInstance loadBpmnModel(String processDefinitionId) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        try {
            return repositoryService.getBpmnModelInstance(processDefinitionId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveActivityName(BpmnModelInstance model, String activityId) {
        if (model == null || !StringUtils.hasText(activityId)) {
            return null;
        }
        try {
            ModelElementInstance el = model.getModelElementById(activityId);
            if (el instanceof FlowNode) {
                String n = ((FlowNode) el).getName();
                return StringUtils.hasText(n) ? n : null;
            }
        } catch (RuntimeException ignored) {
            // 元素缺失等
        }
        return null;
    }
}
