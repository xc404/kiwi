package com.kiwi.project.bpm.dto;

import lombok.Data;

/**
 * 未关闭的引擎 Incident（与 {@link org.camunda.bpm.engine.runtime.Incident} 对应）。
 */
@Data
public class BpmOpenIncidentDto {

    private String incidentId;
    private String incidentType;
    private String message;
    /** 关联 BPMN 节点 id，部分类型可能为空 */
    private String activityId;
    private String activityName;
}
