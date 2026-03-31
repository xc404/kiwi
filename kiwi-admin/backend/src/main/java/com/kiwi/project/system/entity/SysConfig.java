package com.kiwi.project.system.entity;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.common.excel.Excel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;


/**
 * 参数配置表 sys_config
 *
 * @author ruoyi
 */

@EqualsAndHashCode(callSuper = true)
@Entity
@Document("sys_config")
@Table(name = "sys_config", catalog = "test", schema = "test", comment = "参数配置表")
@Data
public class SysConfig extends BaseEntity<String>
{

    /**
     * 参数名称
     */
    @Column(name = "config_name", comment = "参数名称")
    @Excel(name = "参数名称")
    private String configName;

    /**
     * 参数键名
     */
    @Excel(name = "参数键名")
    private String configKey;

    /**
     * 参数键值
     */
    @Excel(name = "参数键值")
    private String configValue;

    /**
     * 系统内置（Y是 N否）
     */
    @Excel(name = "系统内置", readConverterExp = "Y=是,N=否")
    private String configType;

    @Excel(name = "备注")
    private String remark;

}
