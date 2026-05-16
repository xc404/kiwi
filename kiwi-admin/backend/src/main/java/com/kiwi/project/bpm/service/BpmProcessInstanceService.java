package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dto.BpmActivityPointerDto;
import com.kiwi.project.bpm.dto.BpmOpenIncidentDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceDto;
import com.kiwi.project.bpm.dto.BpmProcessInstanceStateDto;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BpmProcessInstanceService {

    /** {@link #batchStateDtosInRequestOrder} 单次请求 id 上限（含去重后） */
    public static final int MAX_INSTANCE_IDS_PER_BATCH = 200;

    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    public BpmProcessInstanceService(ProcessEngine processEngine) {
        this.historyService = processEngine.getHistoryService();
        this.runtimeService = processEngine.getRuntimeService();
        this.repositoryService = processEngine.getRepositoryService();
    }

    /**
     * 轻量状态：供 cryoEMS 等轮询；不存在时返回 empty（HTTP 层映射 404）。
     */
    public Optional<BpmProcessInstanceStateDto> findStateDto(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return Optional.empty();
        }
        HistoricProcessInstance hip = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId.trim())
                .singleResult();
        if (hip == null) {
            return Optional.empty();
        }
        List<Incident> incidents =
                runtimeService.createIncidentQuery().processInstanceId(processInstanceId.trim()).list();
        return Optional.of(buildStateDto(hip, incidents, new HashMap<>()));
    }

    /**
     * 批量状态：返回顺序与请求 {@code instanceIds} 一致（去重后）；历史不存在的 id 返回 {@code found=false}。
     */
    public List<BpmProcessInstanceStateDto> batchStateDtosInRequestOrder(List<String> rawInstanceIds) {
        List<String> ids = sanitizeInstanceIds(rawInstanceIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        String[] idArray = ids.toArray(String[]::new);
        List<HistoricProcessInstance> hips = historyService.createHistoricProcessInstanceQuery()
                .processInstanceIdIn(idArray)
                .list();
        Map<String, HistoricProcessInstance> hipById = hips.stream()
                .collect(Collectors.toMap(HistoricProcessInstance::getId, h -> h, (a, b) -> a));

        Map<String, List<Incident>> incByPi = new HashMap<>();
        for (HistoricProcessInstance hip : hips) {
            if (hip.getEndTime() == null) {
                List<Incident> inc =
                        runtimeService.createIncidentQuery().processInstanceId(hip.getId()).list();
                if (!inc.isEmpty()) {
                    incByPi.put(hip.getId(), inc);
                }
            }
        }

        Map<String, BpmnModelInstance> bpmnCache = new HashMap<>();
        List<BpmProcessInstanceStateDto> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            HistoricProcessInstance hip = hipById.get(id);
            if (hip == null) {
                BpmProcessInstanceStateDto missing = new BpmProcessInstanceStateDto();
                missing.setId(id);
                missing.setFound(false);
                out.add(missing);
            } else {
                out.add(buildStateDto(hip, incByPi.getOrDefault(id, List.of()), bpmnCache));
            }
        }
        return out;
    }

    private static List<String> sanitizeInstanceIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String s : raw) {
            if (!StringUtils.hasText(s)) {
                continue;
            }
            if (seen.size() >= MAX_INSTANCE_IDS_PER_BATCH) {
                break;
            }
            seen.add(s.trim());
        }
        return new ArrayList<>(seen);
    }

    private BpmProcessInstanceStateDto buildStateDto(
            HistoricProcessInstance hip, List<Incident> incidents, Map<String, BpmnModelInstance> bpmnCache) {
        BpmProcessInstanceStateDto dto = new BpmProcessInstanceStateDto();
        dto.setId(hip.getId());
        dto.setFound(true);
        dto.setEndTime(hip.getEndTime());
        dto.setDeleteReason(hip.getDeleteReason());

        if (hip.getEndTime() != null) {
            dto.setEnded(true);
            dto.setSuspended(false);
            boolean canceled = StringUtils.hasText(hip.getDeleteReason());
            dto.setState((canceled ? ProcessInstanceState.CANCELED : ProcessInstanceState.COMPLETED).name());
            dto.setErrorReason(null);
            dto.setErrorActivityId(null);
            dto.setErrorActivityName(null);
            return dto;
        }

        dto.setEnded(false);
        String defId = hip.getProcessDefinitionId();
        BpmnModelInstance bpmnModel = null;
        if (StringUtils.hasText(defId)) {
            bpmnModel = bpmnCache.computeIfAbsent(defId, this::loadBpmnModel);
        }
        List<BpmOpenIncidentDto> openRows = mapOpenIncidents(incidents, bpmnModel);
        fillErrorSummary(dto, openRows);

        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(hip.getId())
                .singleResult();
        if (pi != null) {
            dto.setSuspended(pi.isSuspended());
            if (!openRows.isEmpty()) {
                dto.setState(ProcessInstanceState.ERROR.name());
            } else if (pi.isSuspended()) {
                dto.setState(ProcessInstanceState.SUSPENDED.name());
            } else {
                dto.setState(ProcessInstanceState.RUNNING.name());
            }
        } else {
            dto.setSuspended(false);
            dto.setState(!openRows.isEmpty() ? ProcessInstanceState.ERROR.name() : ProcessInstanceState.ACTIVE.name());
        }
        return dto;
    }

    private static void fillErrorSummary(BpmProcessInstanceStateDto dto, List<BpmOpenIncidentDto> openRows) {
        if (openRows.isEmpty()) {
            dto.setErrorReason(null);
            dto.setErrorActivityId(null);
            dto.setErrorActivityName(null);
            return;
        }
        BpmOpenIncidentDto first = openRows.get(0);
        dto.setErrorActivityId(first.getActivityId());
        dto.setErrorActivityName(first.getActivityName());
        if (openRows.size() == 1) {
            dto.setErrorReason(first.getMessage());
        } else {
            dto.setErrorReason(openRows.stream()
                    .map(BpmOpenIncidentDto::getMessage)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("; ")));
        }
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
