package com.kiwi.project.bpm.dto;

import lombok.Data;

/**
 * 流程实例当前停留的 BPMN 节点（可能多条：并行、多实例等）。
 */
@Data
public class BpmActivityPointerDto {

    private String activityId;
    /** BPMN 上配置的节点名称，可能为空 */
    private String activityName;
    /** 引擎活动类型，如 {@code userTask}、{@code serviceTask} */
    private String activityType;
}
