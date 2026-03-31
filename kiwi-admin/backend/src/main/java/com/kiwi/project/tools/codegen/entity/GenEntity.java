package com.kiwi.project.tools.codegen.entity;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 业务表 gen_table
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class GenEntity extends BaseEntity<String>
{

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 表描述
     */
    private String tableComment;

    /**
     * 表存储引擎
     */
    private String tableCatalog;
    /**
     * 表所属数据库schema
     */
    private String tableSchema;


    /**
     * 关联父表的表名
     */
    private String subTableName;

    /**
     * 本表关联父表的外键名
     */
    private String subTableFkName;

    /**
     * 实体类名称(首字母大写)
     */
    private String className;


    private GenEnum.DatabaseType databaseType;

    /**
     * 使用的模板（crud单表操作 tree树表操作 sub主子表操作）
     */
    private GenEnum.GenTpl genTpl;

    /**
     * 前端类型（element-ui模版 element-plus模版）
     */
    private GenEnum.WebTpl webTpl;

    /**
     * 持久层类型（mybatis-plus 、mongo）
     */
    private GenEnum.DaoTpl daoTpl;

    /**
     * 生成包路径
     */
    private String packageName;

    /**
     * 生成模块名
     */
    private String moduleName;

    /**
     * 生成业务名
     */
    private String businessName;

    /**
     * 生成功能名
     */
    private String functionName;

    /**
     * 生成作者
     */
    private String functionAuthor;

//    /**
//     * 生成代码方式（0zip压缩包 1自定义路径）
//     */
//    private String genType;

    /**
     * 生成路径（不填默认项目路径）
     */
    private String genPath;


    /**
     * 其它生成选项
     */
    private String options;

    /**
     * 树编码字段
     */
    private String treeField;

    /**
     * 树父编码字段
     */
    private String treeParentField;

    /**
     * 树名称字段
     */
    private String treeNameField;

    /**
     * 上级菜单ID字段
     */
    private String parentMenuId;

}

