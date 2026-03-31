package com.kiwi.project.bpm.model;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document
public class BpmComponent extends BaseEntity<String>
{

    public enum Type {
        JavaClass,
        SpringBean,
        RestApi,
        SpringExternalTask
    }
    private String parentId;
    private String key;
    private String source;
    private String name;
    private String description;
    private String group = "common";
    private Type type = Type.SpringBean;
    private String version;
    private List<BpmComponentParameter> inputParameters;
    private List<BpmComponentParameter> outputParameters;
}
