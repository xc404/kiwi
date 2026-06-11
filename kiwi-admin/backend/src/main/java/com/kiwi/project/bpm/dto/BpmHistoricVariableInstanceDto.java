package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "流程实例历史变量")
public class BpmHistoricVariableInstanceDto {

    private String name;
    private String type;
    private Object value;
    private String activityInstanceId;
    private Date createTime;
}
