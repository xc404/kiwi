package com.kiwi.project.bpm.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 流程实例：分页列表行与单实例状态查询（如 cryoEMS 等机机查询）共用载荷。
 * <p>列表接口仅填充业务与定义维度字段；状态接口额外填充 {@code state}、{@code ended} 等。
 */
@Data
public class BpmProcessInstanceDto {

    private String id;
    private String businessKey;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processDefinitionName;
    private Date startTime;
    private String tenantId;

    /**
     * 单实例查询等场景填充；列表行可为空。
     */
    private ProcessInstanceState state;
    private Boolean ended;
    private Boolean suspended;
    private Date endTime;
    private String deleteReason;

    /**
     * 单实例详情：当前未结束的 BPMN 活动（并行/多实例时可能多条）。
     */
    private List<BpmActivityPointerDto> currentActivities;

    /**
     * 单实例详情：未关闭的 incident（存在时 {@link #state} 一般为 {@link ProcessInstanceState#ERROR}）。
     */
    private List<BpmOpenIncidentDto> openIncidents;
}
