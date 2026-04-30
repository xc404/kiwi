package com.kiwi.project.bpm.dto;

import lombok.Data;

import java.util.Date;

/**
 * 机机查询 Camunda 流程实例状态（运行中 / 历史）。
 */
@Data
public class ProcessInstanceStateDto {

    private String id;
    /**
     * RUNNING | SUSPENDED | COMPLETED | CANCELED | ACTIVE（历史表中未结束且无运行实例时的兜底）
     */
    private String state;
    private Boolean ended;
    private Boolean suspended;
    private Date endTime;
    private String deleteReason;
}
