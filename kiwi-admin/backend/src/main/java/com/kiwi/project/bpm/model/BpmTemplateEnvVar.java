package com.kiwi.project.bpm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document("bpmTemplateEnvVar")
@CompoundIndex(name = "uk_pack_key", def = "{'packId': 1, 'key': 1}", unique = true)
public class BpmTemplateEnvVar extends BaseEntity<String> {

    private String packId;
    private String key;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String value;
    private Boolean encrypted;
    private String description;
    private Integer sort;
}
