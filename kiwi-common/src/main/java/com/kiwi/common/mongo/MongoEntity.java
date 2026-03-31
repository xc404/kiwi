package com.kiwi.common.mongo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kiwi.common.entity.IdEntity;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.util.Date;

@Data
public class MongoEntity implements IdEntity<String>
{
    @Id
    private String id;

    @CreatedBy
    /** 创建者 */
    private String createBy;

    @CreatedDate
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @LastModifiedBy
    /** 更新者 */
    private String updateBy;

    @LastModifiedDate
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
