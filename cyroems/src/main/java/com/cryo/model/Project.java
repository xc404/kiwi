package com.cryo.model;

import com.cryo.common.model.IdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("project")
@EqualsAndHashCode(callSuper = true)
@Data
public class Project extends IdEntity {

    @Indexed(unique = true)
    private String pid;

    private String title;
    private String contact_name;
    private String contact_email;
    private String manager;
    private String manager_email;
    private String description;

    @Indexed
    private String belong_group;   // 关联 Group._id

    private String type;
    private String valid_util;     // 有效期，格式 "yyyy-MM-dd"
    private String organization;
    private Integer rating;
}
