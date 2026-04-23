package com.kiwi.project.bpm.model;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class BpmProcess extends BaseEntity<String>
{
    private String name;
    private String bpmnXml;
    private String projectId;

    private int version;

    private int deployedVersion;
    private Date deployedAt;
    private String deployedProcessDefinitionId;

    /**
     * 该流程在 Camunda 中允许同时存在的**运行中**流程实例数量上限（按流程定义 key 统计，含多版本部署产生的实例）。
     * null 或 0 表示不限制。
     */
    private Integer maxProcessInstances;
}
