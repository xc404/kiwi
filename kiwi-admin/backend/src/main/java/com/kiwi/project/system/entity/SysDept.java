package com.kiwi.project.system.entity;


import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.tree.Node;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门表 sys_dept
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysDept extends BaseEntity<String> implements Node
{

    /**
     * 部门名称
     */
    private String name;

    /**
     * 父部门ID
     */
    private String parentId;

//    /**
//     * 父部门名称
//     */
//    private String parentName;
//
//    /**
//     * 祖级列表
//     */
//    private String ancestors;


    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * 负责人
     */
    private String manager;

//    /**
//     * 联系电话
//     */
//    private String phone;
//
//    /**
//     * 邮箱
//     */
//    private String email;

    /**
     * 部门状态:0正常,1停用
     */
    private String status;


    public boolean enabled() {
        return "0".equals(this.status);
    }

}
