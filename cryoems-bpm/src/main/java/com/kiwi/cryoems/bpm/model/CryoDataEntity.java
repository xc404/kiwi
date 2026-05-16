package com.kiwi.cryoems.bpm.model;

import com.kiwi.common.entity.IdEntity;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.util.Date;

/**
 * 与 cyroems {@code com.cryo.common.model.DataEntity} 字段命名对齐，便于共用 Mongo 集合。
 */
@Data
public class CryoDataEntity implements IdEntity<String> {

    @Id
    private String id;

    @CreatedDate
    private Date created_at;

    @LastModifiedDate
    private Date updated_at;
}
