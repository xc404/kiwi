package com.kiwi.project.system.entity;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.tree.Node;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 菜单权限表 sys_menu
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysMenu extends BaseEntity<String> implements Node
{
    @Serial
    private static final long serialVersionUID = 1L;
    public static String Root = "0";

    /**
     * 菜单名称
     */
    private String name;


    /**
     * 父菜单ID
     */
    private String parentId;

    /**
     * 显示顺序
     */
    private Integer sort = 10;

    /**
     * 路由地址
     */
    private String path;


    /**
     * 是否为外链（0是 1否）
     */
    private String isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    private String isCache;

    /**
     * 类型（M目录 C菜单）
     */
    private String menuType;

    /**
     * 显示状态（0显示 1隐藏）
     */
    private boolean visible = true;

    /**
     * 菜单状态（0正常 1停用）
     */
    private String status;

    /**
     * 权限字符串
     */
    private List<SysMenuPermission> permissions;

    /**
     * 菜单图标
     */
    private String icon;


//    public boolean enabled() {
//        return Optional.ofNullable(this.status).map(s -> s.equals("0")).orElse(true);
//    }


    public boolean isVisible() {
        return visible && "0".equals(status);
    }

}
