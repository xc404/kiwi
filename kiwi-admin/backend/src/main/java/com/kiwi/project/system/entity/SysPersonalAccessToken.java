package com.kiwi.project.system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 用户个人长期访问令牌（与 Sa-Token 终端一一对应，集合名便于运维识别）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "sys_personal_access_token")
public class SysPersonalAccessToken extends BaseEntity<String> {

    private static final long serialVersionUID = 1L;

    /** 所属用户（sys_user._id） */
    private String userId;

    /** 用户备注名，便于区分用途 */
    private String name;

    /** Sa-Token 终端标识，形如 {@code pat-}{@literal <mongoId>} */
    private String saDevice;

    /** 令牌脱敏展示（仅创建时写入，不可逆） */
    private String tokenHint;

    /** 预计过期时间（与签发时 Sa-Token timeout 一致） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;
}
