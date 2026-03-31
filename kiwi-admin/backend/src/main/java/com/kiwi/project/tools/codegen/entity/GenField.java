package com.kiwi.project.tools.codegen.entity;


import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * 代码生成业务字段表 gen_table_column
 *
 * @author ruoyi
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class GenField extends BaseEntity<String>
{
    private static final long serialVersionUID = 1L;

    /**
     * 归属表编号
     */
    private String entityId;

    /**
     * 列名称
     */
    private String columnName;

    /**
     * 列描述
     */
    private String columnComment;

    /**
     * 列类型
     */
    private JdbcType columnType;


    private String columnDefinition;
    private int length;
    private int precision;
    private int scale;
    private int secondPrecision;

    /**
     * JAVA类型
     */
    private JavaType javaType;

    /**
     * JAVA字段名
     */
    private String javaField;

    /**
     * 是否主键（1是）
     */
    private boolean isPk;

    /**
     * 是否自增（1是）
     */
    private boolean autoIncrement;

    /**
     * 是否必填（1是）
     */
    private boolean required;

    /**
     * 是否为插入字段（1是）
     */
    private boolean insertable;

    /**
     * 是否编辑字段（1是）
     */
    private boolean updatable;

    /**
     * 是否列表字段（1是）
     */
    private boolean inColumn;

    /**
     * 是否查询字段（1是）
     */
    private boolean inQuery;

    /**
     * 查询方式（EQ等于、NE不等于、GT大于、LT小于、LIKE模糊、BETWEEN范围）
     */
    private QueryType queryType;

    /**
     * 显示类型（input文本框、textarea文本域、select下拉框、checkbox复选框、radio单选框、datetime日期控件、image图片上传控件、upload文件上传控件、editor富文本控件）
     */
    private HtmlType htmlType;

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 排序
     */
    private Integer sort;
    private boolean unique;


    public static boolean isSuperColumn(String javaField) {
        return StringUtils.equalsAnyIgnoreCase(javaField,
                // BaseEntity
                "createBy", "createTime", "updateBy", "updateTime");
    }


}