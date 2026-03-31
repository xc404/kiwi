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
}
