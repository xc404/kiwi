package com.kiwi.project.system.entity;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.excel.Excel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 角色表 sys_role
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysRole extends BaseEntity<String>
{
    private static final long serialVersionUID = 1L;

    public static final String Admin = "admin";

    /**
     * 角色名称
     */
    @Excel(name = "角色名称")
    private String name;

    /**
     * 角色权限字符串
     */
    private String code;


    /**
     * 角色排序
     */
    @Excel(name = "角色排序")
    private Integer sort;


    /**
     * 菜单组
     */
    private Set<String> menuIds;


    /**
     * 角色菜单权限
     */
    private Set<String> permissions;


    public void setCode(String code) {
        this.setId(code);
        this.code = code;
    }
}
