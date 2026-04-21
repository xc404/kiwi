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
        SpringExternalTask,
        CallActivity
    }
    private String parentId;
    private String key;
    private String source;
    private String name;
    private String description;
    private String group ;
    private Type type;
    private String version;
    /** 元数据 SHA-256 指纹，用于自动部署时跳过未变更写库 */
    private String deploymentSignature;
    private List<BpmComponentParameter> inputParameters;
    private List<BpmComponentParameter> outputParameters;
}
