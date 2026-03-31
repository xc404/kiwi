package com.kiwi.project.tools.codegen.utils;

import cn.hutool.core.util.StrUtil;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.GenEnum;
import com.kiwi.project.tools.codegen.entity.GenField;
import com.kiwi.project.tools.codegen.entity.HtmlType;
import com.kiwi.project.tools.codegen.entity.JavaType;
import com.kiwi.project.tools.codegen.entity.JdbcType;
import com.kiwi.project.tools.codegen.entity.QueryType;
import com.kiwi.project.tools.codegen.service.GenConfig;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

import static cn.hutool.core.text.NamingCase.toCamelCase;
import static cn.hutool.core.text.NamingCase.toSymbolCase;

/**
 * 代码生成器 工具类
 *
 * @author ruoyi
 */
public class GenUtils
{
    /**
     * 初始化表信息
     */
    public static void initTable(GenEntity genEntity) {
        if( StringUtils.isBlank(genEntity.getClassName()) ) {
            genEntity.setClassName(StrUtil.upperFirst(convertClassName(genEntity.getTableName())));
        }
        if( StringUtils.isBlank(genEntity.getTableName()) ) {
            genEntity.setTableName(StrUtil.lowerFirst(genEntity.getClassName()));
        }
        genEntity.setGenTpl(GenEnum.GenTpl.CRUD);
        genEntity.setWebTpl(GenEnum.WebTpl.Angular);
        if( genEntity.getDatabaseType() == null ) {
            genEntity.setDatabaseType(GenEnum.DatabaseType.MongoDB);
        }
        if( genEntity.getDaoTpl() == null ) {
            if( genEntity.getDatabaseType().isSql() ) {
                genEntity.setDaoTpl(GenEnum.DaoTpl.MybatisPlus);
            } else {
                genEntity.setDaoTpl(GenEnum.DaoTpl.MongoDB);
            }
        }
//        genEntity.setDaoTpl(GenEnum.DaoTpl.MYBATIS_PLUS);

        if( StringUtils.isBlank(genEntity.getTableComment()) ) {
            genEntity.setTableComment(genEntity.getTableName());
        }
        if( StringUtils.isBlank(genEntity.getPackageName()) ) {
            genEntity.setPackageName(GenConfig.getPackageName());
        }
        if( StringUtils.isBlank(genEntity.getModuleName()) ) {

            genEntity.setModuleName(getModuleName(GenConfig.getPackageName()));
        }
        if( StringUtils.isBlank(genEntity.getBusinessName()) ) {

            genEntity.setBusinessName(getBusinessName(genEntity.getTableName()));
        }
        if( StringUtils.isBlank(genEntity.getFunctionName()) ) {

            genEntity.setFunctionName(replaceText(genEntity.getTableComment()));
        }
        if( StringUtils.isBlank(genEntity.getFunctionAuthor()) ) {
            genEntity.setFunctionAuthor(GenConfig.getAuthor());
        }
//        if( StringUtils.isBlank(genEntity.getGenType()) ) {
//            genEntity.setGenType(GenEnum.GenTpl.CRUD.getCode());
//        }

    }

    /**
     * 初始化列属性字段
     */
    public static void initColumnField(GenField field) {

        if( field.getJavaType() == null && field.getColumnType() == null ) {
            throw new RuntimeException("请设置字段类型");
        }
        if( field.getJavaField() == null && field.getColumnName() == null ) {
            throw new RuntimeException("请选择字段名称");
        }
        if( field.getColumnType() == null ) {
            field.setColumnType(toJdbcType(field.getJavaType()));
        }
        if( field.getJavaType() == null ) {
            field.setJavaType(toJavaType(field.getColumnType()));
        }
        if( field.getColumnName() == null ) {
            field.setColumnName(field.getJavaField());
        }
        if( field.getJavaField() == null ) {
            field.setJavaField(toCamelCase(field.getColumnName()));
        }

        if( GenConstants.ID.equals(field.getJavaField()) ) {
            field.setPk(true);
        }


        JavaType javaType = field.getJavaType();
        JdbcType jdbcType = field.getColumnType();
        String dataType = jdbcType.name().toLowerCase();
        // 设置java字段名
        String javaField = field.getJavaField();
        // 设置默认类型
//        field.setQueryType(QueryParam.Type.EQ);
        field.setHtmlType(HtmlType.INPUT);

        if( arraysContains(GenConstants.COLUMNTYPE_STR, dataType) || arraysContains(GenConstants.COLUMNTYPE_TEXT, dataType) ) {
            // 字符串长度超过500设置为文本域
            Integer columnLength = field.getLength();
            HtmlType htmlType = columnLength >= 500 || arraysContains(GenConstants.COLUMNTYPE_TEXT, dataType) ? HtmlType.TEXTAREA : HtmlType.INPUT;
            field.setHtmlType(htmlType);
        } else if( arraysContains(GenConstants.COLUMNTYPE_TIME, dataType) ) {
            field.setHtmlType(HtmlType.DATE);
        } else if( arraysContains(GenConstants.COLUMNTYPE_NUMBER, dataType) ) {
            field.setHtmlType(HtmlType.INPUT);
        }

        if( !field.isPk() ) {
            // 插入字段（默认所有字段都需要插入）
            field.setInsertable(true);
        }


        // 编辑字段
        if( !arraysContains(GenConstants.COLUMNNAME_NOT_EDIT, javaField) && !field.isPk() ) {
            field.setUpdatable(true);
        }
        // 列表字段
        if( !arraysContains(GenConstants.COLUMNNAME_NOT_LIST, javaField) ) {
            field.setInColumn(true);
        }
//        // 查询字段
//        if( !arraysContains(GenConstants.COLUMNNAME_NOT_QUERY, javaField) && !field.isPk() ) {
//            field.setInQuery(true);
//        }

        // 查询字段类型
        if( StringUtils.endsWithIgnoreCase(javaField, "name") ) {
            field.setQueryType(QueryType.LIKE);
            field.setInQuery(true);
        }
        if( StringUtils.endsWithIgnoreCase(javaField, "status") ) {
//            field.setQueryType(QueryType.EQ);
            field.setQueryType(QueryType.IN);
            field.setInQuery(true);
            field.setHtmlType(HtmlType.SELECT);
        }

        // 状态字段设置单选框
        if( StringUtils.endsWithIgnoreCase(javaField, "sex") ) {
            field.setHtmlType(HtmlType.RADIO);
        }
        // 类型&性别字段设置下拉框
        else if( StringUtils.endsWithIgnoreCase(javaField, "type")
        ) {
            field.setHtmlType(HtmlType.SELECT);
        }
        // 图片字段设置图片上传控件
        else if( StringUtils.endsWithIgnoreCase(javaField, "image") ) {
            field.setHtmlType(HtmlType.IMAGE_UPLOAD);
        }
        // 文件字段设置文件上传控件
        else if( StringUtils.endsWithIgnoreCase(javaField, "file") ) {
            field.setHtmlType(HtmlType.FILE_UPLOAD);
        }
        // 内容字段设置富文本控件
        else if( StringUtils.endsWithIgnoreCase(javaField, "content") ) {
            field.setHtmlType(HtmlType.EDITOR);
        }

        if( StringUtils.isBlank(field.getColumnComment()) ) {
            field.setColumnComment(StrUtil.upperFirst(toSymbolCase(javaField, ' ')));
        }
    }

    private static JdbcType toJdbcType(JavaType javaType) {
        return switch( javaType ) {
            case String -> JdbcType.VARCHAR;
            case Integer -> JdbcType.INTEGER;
            case Long -> JdbcType.BIGINT;
            case Double -> JdbcType.DOUBLE;
            case BigDecimal -> JdbcType.DECIMAL;
            case Date -> JdbcType.TIMESTAMP;
            case Boolean -> JdbcType.BOOLEAN;
            case Short -> JdbcType.SMALLINT;
            default -> JdbcType.VARCHAR;
        };
    }

    public static JavaType toJavaType(JdbcType jdbcType) {

        return switch( jdbcType ) {
            case VARCHAR -> JavaType.String;
            case INTEGER -> JavaType.Integer;
            case BIGINT -> JavaType.Long;
            case DOUBLE -> JavaType.Double;
            case DECIMAL -> JavaType.BigDecimal;
            case TIMESTAMP, TIME, DATE -> JavaType.Date;
            case BOOLEAN -> JavaType.Boolean;
            case SMALLINT -> JavaType.Short;
            default -> JavaType.String;
        };

    }

    /**
     * 校验数组是否包含指定值
     *
     * @param arr         数组
     * @param targetValue 值
     * @return 是否包含
     */
    public static boolean arraysContains(String[] arr, String targetValue) {
        return Arrays.asList(arr).contains(targetValue);
    }

    /**
     * 获取模块名
     *
     * @param packageName 包名
     * @return 模块名
     */
    public static String getModuleName(String packageName) {
        int lastIndex = packageName.lastIndexOf(".");
        int nameLength = packageName.length();
        return StringUtils.substring(packageName, lastIndex + 1, nameLength);
    }

    /**
     * 获取业务名
     *
     * @param tableName 表名
     * @return 业务名
     */
    public static String getBusinessName(String tableName) {
        int lastIndex = tableName.lastIndexOf("_");
        int nameLength = tableName.length();
        return StringUtils.substring(tableName, lastIndex + 1, nameLength);
    }

    /**
     * 表名转换成Java类名
     *
     * @param tableName 表名称
     * @return 类名
     */
    public static String convertClassName(String tableName) {
        boolean autoRemovePre = GenConfig.getAutoRemovePre();
        String tablePrefix = GenConfig.getTablePrefix();
        if( autoRemovePre && StringUtils.isNotEmpty(tablePrefix) ) {
            String[] searchList = StringUtils.split(tablePrefix, ",");
            tableName = replaceFirst(tableName, searchList);
        }
        return toCamelCase(tableName);
    }

    /**
     * 批量替换前缀
     *
     * @param replacementm 替换值
     * @param searchList   替换列表
     * @return
     */
    public static String replaceFirst(String replacementm, String[] searchList) {
        String text = replacementm;
        for( String searchString : searchList ) {
            if( replacementm.startsWith(searchString) ) {
                text = replacementm.replaceFirst(searchString, "");
                break;
            }
        }
        return text;
    }

    /**
     * 关键字替换
     *
     * @param text 需要被替换的名字
     * @return 替换后的名字
     */
    public static String replaceText(String text) {
        return RegExUtils.replaceAll(text, "(?:表|若依)", "");
    }

    /**
     * 获取数据库类型字段
     *
     * @param columnType 列类型
     * @return 截取后的列类型
     */
    public static String getDbType(String columnType) {
        if( StringUtils.indexOf(columnType, "(") > 0 ) {
            return StringUtils.substringBefore(columnType, "(");
        } else {
            return columnType;
        }
    }

    /**
     * 获取字段长度
     *
     * @param columnType 列类型
     * @return 截取后的列类型
     */
    public static Integer getColumnLength(String columnType) {
        if( StringUtils.indexOf(columnType, "(") > 0 ) {
            String length = StringUtils.substringBetween(columnType, "(", ")");
            return Integer.valueOf(length);
        } else {
            return 0;
        }
    }

    public static boolean isSuperColumn(String javaField) {
        return StringUtils.equalsAnyIgnoreCase(javaField,
                // BaseEntity
                "id", "createBy", "createTime", "updateBy", "updateTime");
    }
}
