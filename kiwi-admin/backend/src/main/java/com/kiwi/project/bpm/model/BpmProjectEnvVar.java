package com.kiwi.project.bpm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * BPM 项目环境变量：按 {@link #projectId} 归属，启动流程时注入引擎。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("bpmProjectEnvVar")
@CompoundIndex(name = "uk_project_key", def = "{'projectId': 1, 'key': 1}", unique = true)
public class BpmProjectEnvVar extends BaseEntity<String> {

    private String projectId;

    /** 变量名，项目内唯一，建议大写下划线 */
    private String key;

    private String value;

    /** true 时 value 以 AES 存储，API 不回显明文 */
    private Boolean encrypted;

    private String description;

    private Integer sort;

    @JsonIgnore
    public String getValue() {
        return value;
    }
}
