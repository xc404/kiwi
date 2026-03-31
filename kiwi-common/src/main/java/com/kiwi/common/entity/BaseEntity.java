package com.kiwi.common.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.util.Date;

/**
 * Entity基类
 *
 * @author ruoyi
 */
@Data
public class BaseEntity<ID> implements IdEntity<ID>
{


    @Id
    private ID id;

    @CreatedBy
    /** 创建者 */
    private String createdBy;

    @CreatedDate
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;

    @LastModifiedBy
    /** 更新者 */
    private String updatedBy;

    @LastModifiedDate
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedTime;


}
