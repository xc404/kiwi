package com.kiwi.project.bpm.model;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document("bpmTemplateProcess")
@CompoundIndex(name = "uk_pack_process_key", def = "{'packId': 1, 'processKey': 1}", unique = true)
public class BpmTemplateProcess extends BaseEntity<String> {

    private String packId;
    private String processKey;
    private String name;
    private String bpmnXml;
    private String artifactUrl;
    private boolean entry;
    private Integer sort;
}
