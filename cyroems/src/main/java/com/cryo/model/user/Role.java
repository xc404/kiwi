package com.cryo.model.user;

import com.cryo.common.model.DataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document("role")
public class Role extends DataEntity
{
    @Indexed(unique = true)
    private Integer role_id;        // 数字 ID

    @Indexed(unique = true)
    private String role_name;       // 唯一标识，如 "admin", "group_admin"

    private List<String> role_access;

    private String display_name;

    // 静态常量，兼容旧 enum 的 Role.super_admin 用法
    public static final String NORMAL       = "normal";
    public static final String VIEWER       = "viewer";
    public static final String GROUP_AMDMIN  = "group_admin";
    public static final String DEVICE_ADMIN = "device_admin";
    public static final String ADMIN       = "admin";
    public static final String SUPER_ADMIN  = "super_admin";

    /** 兼容旧 enum 的 name() 用法 */
    public String name() {
        return role_name;
    }

    public boolean is(String roleName) {
        return roleName != null && roleName.equals(this.role_name);
    }
}
