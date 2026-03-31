package com.kiwi.project.system.entity;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SysMenuPermission extends BaseEntity<String>
{
    private String menuId;
    private String permission;
    private boolean optional;
}
