package com.kiwi.project.bpm.model;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.tree.Node;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BpmProject extends BaseEntity<String>
{
    /**
     * 菜单名称
     */
    private String name;

//
//    /**
//     * 父菜单ID
//     */
//    private String parentId;
//
//    /**
//     * 显示顺序
//     */
//    private Integer sort = 10;
}
