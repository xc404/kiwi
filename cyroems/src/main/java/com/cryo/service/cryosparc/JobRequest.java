package com.cryo.service.cryosparc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobRequest
{
    private String project_uid;
    private String workspace_uid;
    private JobType job_type;
    private Map<String, Object> params; // 使用 Map 处理动态参数
    private String ancestor_job_uid;

    // Getters and Setters
}