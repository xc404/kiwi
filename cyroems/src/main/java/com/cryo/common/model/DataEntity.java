package com.cryo.common.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataEntity extends IdEntity {
    @CreatedDate
    private Date created_at;
    @LastModifiedDate
    private Date updated_at;
}
