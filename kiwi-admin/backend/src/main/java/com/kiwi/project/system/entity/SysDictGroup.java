package com.kiwi.project.system.entity;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.excel.Excel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型表 sys_dict_type
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysDictGroup extends BaseEntity<String>
{
    private static final long serialVersionUID = 1L;

    public final static String StatusEnabled = "0";
    public final static String StatusDisabled = "1";

    /**
     * 字典类型
     */
    @Excel(name = "字典键值")
    private String groupCode;

    /**
     * 字典名称
     */
    @Excel(name = "字典名称")
    private String groupName;

    /**
     * 状态（0正常 1停用）
     */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status = StatusEnabled;

    @Excel(name = "备注")
    private String remark;


    public void setGroupCode(String groupCode) {
        this.setId(groupCode);
    }

    public boolean enabled() {
        return this.status.equals(StatusEnabled);
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        this.groupCode = (String) id;
    }
}
