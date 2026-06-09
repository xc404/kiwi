package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Schema(description = "流程实例历史活动（画布高亮）")
public class BpmHistoricActivityInstanceDto {

    private String id;
    private String activityId;
    private String activityType;
    private Date startTime;
    private Date endTime;
    private Boolean canceled;
    private List<String> incidentIds;
    private String calledProcessInstanceId;
    @Schema(description = "是否已结束（endTime 非空）")
    private boolean completed;
    @Schema(description = "是否仍在运行（未结束）")
    private boolean active;
}
