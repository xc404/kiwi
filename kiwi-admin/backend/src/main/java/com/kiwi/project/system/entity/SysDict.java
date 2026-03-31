package com.kiwi.project.system.entity;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.excel.Excel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据表 sys_dict_data
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysDict extends BaseEntity<String>
{

    public static final Integer DefaultSort = 10;

    /**
     * 字典排序
     */
    @Excel(name = "字典排序", cellType = Excel.ColumnType.NUMERIC)
    private Integer dictSort = DefaultSort;

    /**
     * 字典标签
     */
    @Excel(name = "字典标签")
    private String name;

    /**
     * 字典code
     */
    @Excel(name = "字典code")
    private String code;


    @Excel(name = "字典键值")
    private String subGroup;

    /**
     * 字典描述
     */
    private String remark;

    /**
     * 字典子类型
     */
    @Excel(name = "字典类型")
    private String groupCode;

    /**
     * 样式属性（其他样式扩展）
     */
    private String cssClass;

    /**
     * 表格字典样式
     */
    private String listClass;


}
