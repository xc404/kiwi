package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Schema(description = "BPM 项目环境变量（列表/详情；加密项不含 value 明文）")
@Data
public class BpmProjectEnvVarDto {

    private String id;
    private String projectId;
    private String key;
    @Schema(description = "非加密项返回明文；加密项为 null")
    private String value;
    private Boolean encrypted;
    private String description;
    private Integer sort;
    private Date createdTime;
    private Date updatedTime;
}
