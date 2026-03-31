package com.kiwi.project.tools.codegen.utils;

import com.kiwi.project.tools.codegen.entity.GenConstants;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.GenEnum;
import com.kiwi.project.tools.codegen.entity.GenField;
import com.kiwi.project.tools.codegen.entity.vo.CodeGenVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * 模板处理工具类
 *
 * @author ruoyi
 */
public class VelocityUtils
{
    /**
     * 项目空间路径
     */
    private static final String PROJECT_PATH = "main/java";

    /**
     * mybatis空间路径
     */
    private static final String MYBATIS_PATH = "main/resources/mybatis";

    /**
     * 默认上级菜单，系统工具
     */
    private static final String DEFAULT_PARENT_MENU_ID = "0";

    /**
     * 设置模板变量信息
     *
     * @return 模板列表
     */
    public static VelocityContext prepareContext(CodeGenVo codeGenVo) {
        GenEntity genEntity = codeGenVo.getGenEntity();
        List<GenField> fields = codeGenVo.getFields();
        String moduleName = genEntity.getModuleName();
        String businessName = genEntity.getBusinessName();
        String packageName = genEntity.getPackageName();
        String tplCategory = genEntity.getGenTpl().name();
        String functionName = genEntity.getFunctionName();

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("tplCategory", genEntity.getGenTpl().name());
        velocityContext.put("tableName", genEntity.getTableName());
        velocityContext.put("functionName", StringUtils.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
        velocityContext.put("ClassName", genEntity.getClassName());
        velocityContext.put("className", StringUtils.uncapitalize(genEntity.getClassName()));
        velocityContext.put("moduleName", genEntity.getModuleName());
        velocityContext.put("BusinessName", StringUtils.capitalize(genEntity.getBusinessName()));
        velocityContext.put("businessName", genEntity.getBusinessName());
        velocityContext.put("basePackage", getPackagePrefix(packageName));
        velocityContext.put("packageName", packageName);
        velocityContext.put("author", genEntity.getFunctionAuthor());
        velocityContext.put("datetime", new Date());
//        velocityContext.put("pkColumn", genTable.getPkColumn());
        velocityContext.put("importList", getImportList(codeGenVo));
        velocityContext.put("permissionPrefix", getPermissionPrefix(moduleName, businessName));
        velocityContext.put("fields", fields);
        velocityContext.put("entity", genEntity);
//        velocityContext.put("dicts", getDicts(genTable));
//        setMenuVelocityContext(velocityContext, genTable);
//        if( GenConstants.TPL_TREE.equals(tplCategory) ) {
//            setTreeVelocityContext(velocityContext, genTable);
//        }
//        if( GenConstants.TPL_SUB.equals(tplCategory) ) {
//            setSubVelocityContext(velocityContext, genTable);
//        }
        return velocityContext;
    }

//    public static void setMenuVelocityContext(VelocityContext context, GenTable genTable) {
//        String options = genTable.getOptions();
//        JSONObject paramsObj = JSON.parseObject(options);
//        String parentMenuId = getParentMenuId(paramsObj);
//        context.put("parentMenuId", parentMenuId);
//    }

//    public static void setTreeVelocityContext(VelocityContext context, GenTable genTable) {
//        String options = genTable.getOptions();
//        JSONObject paramsObj = JSON.parseObject(options);
//        String treeCode = getTreecode(paramsObj);
//        String treeParentCode = getTreeParentCode(paramsObj);
//        String treeName = getTreeName(paramsObj);
//
//        context.put("treeCode", treeCode);
//        context.put("treeParentCode", treeParentCode);
//        context.put("treeName", treeName);
//        context.put("expandColumn", getExpandColumn(genTable));
//        if( paramsObj.containsKey(GenConstants.TREE_PARENT_CODE) ) {
//            context.put("tree_parent_code", paramsObj.getString(GenConstants.TREE_PARENT_CODE));
//        }
//        if( paramsObj.containsKey(GenConstants.TREE_NAME) ) {
//            context.put("tree_name", paramsObj.getString(GenConstants.TREE_NAME));
//        }
//    }

//    public static void setSubVelocityContext(VelocityContext context, GenTable genTable) {
//        GenTable subTable = genTable.getSubTable();
//        String subTableName = genTable.getSubTableName();
//        String subTableFkName = genTable.getSubTableFkName();
//        String subClassName = genTable.getSubTable().getClassName();
//        String subTableFkClassName = StringUtils.convertToCamelCase(subTableFkName);
//
//        context.put("subTable", subTable);
//        context.put("subTableName", subTableName);
//        context.put("subTableFkName", subTableFkName);
//        context.put("subTableFkClassName", subTableFkClassName);
//        context.put("subTableFkclassName", StringUtils.uncapitalize(subTableFkClassName));
//        context.put("subClassName", subClassName);
//        context.put("subclassName", StringUtils.uncapitalize(subClassName));
//        context.put("subImportList", getImportList(genTable.getSubTable()));
//    }

    /**
     * 获取模板信息
     *
     * @return 模板列表
     */
    public static List<String> getTemplateList(GenEntity genEntity) {
//        String useWebType = "vm/vue";
//        if( "element-plus".equals(tplWebType) ) {
//            useWebType = "vm/vue/v3";
//        }
        List<String> templates = new ArrayList<String>();
//        templates.add("vm/java/domain.java.vm");
//        templates.add("vm/java/mapper.java.vm");
//        templates.add("vm/java/service.java.vm");
//        templates.add("vm/java/serviceImpl.java.vm");
//        templates.add("vm/java/controller.java.vm");
//        templates.add("vm/xml/mapper.xml.vm");
//        templates.add("vm/sql/sql.vm");
//        templates.add("vm/js/api.js.vm");
//        if( GenConstants.TPL_CRUD.equals(tplCategory) ) {
//            templates.add(useWebType + "/index.vue.vm");
//        } else if( GenConstants.TPL_TREE.equals(tplCategory) ) {
//            templates.add(useWebType + "/index-tree.vue.vm");
//        } else if( GenConstants.TPL_SUB.equals(tplCategory) ) {
//            templates.add(useWebType + "/index.vue.vm");
//            templates.add("vm/java/sub-domain.java.vm");
//        }
        templates.add("vm/java/controller.java.vm");
        if( genEntity.getDaoTpl() == GenEnum.DaoTpl.MongoDB ) {
            templates.add("vm/java/mongo/entity.java.vm");
            templates.add("vm/java/mongo/dao.java.vm");
        }

        return templates;
    }

    /**
     * 获取文件名
     */
    public static String getFileName(String template, GenEntity genEntity) {
        // 文件名称
        String fileName = "";
        // 包路径
        String packageName = genEntity.getPackageName();
        // 模块名
        String moduleName = genEntity.getModuleName();
        // 大写类名
        String className = genEntity.getClassName();
        // 业务名称
        String businessName = genEntity.getBusinessName();

        String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
        String mybatisPath = MYBATIS_PATH + "/" + moduleName;
        String vuePath = "vue";
//
        if( template.contains("entity.java") ) {
            fileName = MessageFormat.format("{0}/entity/{1}.java", javaPath, className);
        }
        if( template.contains("dao.java") ) {
            fileName = MessageFormat.format("{0}/dao/{1}Dao.java", javaPath, className);
        }
        if( template.contains("controller.java") ) {
            fileName = MessageFormat.format("{0}/controller/{1}Controller.java", javaPath, className);
        }
//        if( template.contains("sub-domain.java.vm") && StringUtils.equals(GenConstants.TPL_SUB, genTable.getTplCategory()) ) {
//            fileName = StringUtils.format("{}/domain/{}.java", javaPath, genTable.getSubTable().getClassName());
//        } else if( template.contains("mapper.java.vm") ) {
//            fileName = StringUtils.format("{}/mapper/{}Mapper.java", javaPath, className);
//        } else if( template.contains("service.java.vm") ) {
//            fileName = StringUtils.format("{}/service/I{}Service.java", javaPath, className);
//        } else if( template.contains("serviceImpl.java.vm") ) {
//            fileName = StringUtils.format("{}/service/impl/{}ServiceImpl.java", javaPath, className);
//        } else if( template.contains("controller.java.vm") ) {
//            fileName = StringUtils.format("{}/controller/{}Controller.java", javaPath, className);
//        } else if( template.contains("mapper.xml.vm") ) {
//            fileName = StringUtils.format("{}/{}Mapper.xml", mybatisPath, className);
//        } else if( template.contains("sql.vm") ) {
//            fileName = businessName + "Menu.sql";
//        } else if( template.contains("api.js.vm") ) {
//            fileName = StringUtils.format("{}/api/{}/{}.js", vuePath, moduleName, businessName);
//        } else if( template.contains("index.vue.vm") ) {
//            fileName = StringUtils.format("{}/views/{}/{}/index.vue", vuePath, moduleName, businessName);
//        } else if( template.contains("index-tree.vue.vm") ) {
//            fileName = StringUtils.format("{}/views/{}/{}/index.vue", vuePath, moduleName, businessName);
//        }
        return fileName;
    }

    /**
     * 获取包前缀
     *
     * @param packageName 包名称
     * @return 包前缀名称
     */
    public static String getPackagePrefix(String packageName) {
        int lastIndex = packageName.lastIndexOf(".");
        return StringUtils.substring(packageName, 0, lastIndex);
    }

    /**
     * 根据列类型获取导入包
     *
     * @return 返回需要导入的包列表
     */
    public static HashSet<String> getImportList(CodeGenVo codeGenVo) {
//        GenTable genTable = codeGenVo.getGenTable();
        List<GenField> columns = codeGenVo.getFields();
//        GenTable subGenTable = genTable.getSubTable();
        HashSet<String> importList = new HashSet<String>();
//        if( StringUtils.isNotNull(subGenTable) ) {
//            importList.add("java.util.List");
//        }
        for( GenField column : columns ) {
            if( !column.isSuperColumn(column.getJavaField()) && GenConstants.TYPE_DATE.equals(column.getJavaType()) ) {
                importList.add("java.util.Date");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
            } else if( !column.isSuperColumn(column.getJavaField()) && GenConstants.TYPE_BIGDECIMAL.equals(column.getJavaType()) ) {
                importList.add("java.math.BigDecimal");
            }
        }
        return importList;
    }

//    /**
//     * 根据列类型获取字典组
//     *
//     * @param genTable 业务表对象
//     * @return 返回字典组
//     */
//    public static String getDicts(GenTable genTable) {
//        List<GenTableColumn> columns = genTable.getColumns();
//        Set<String> dicts = new HashSet<String>();
//        addDicts(dicts, columns);
//        if( StringUtils.isNotNull(genTable.getSubTable()) ) {
//            List<GenTableColumn> subColumns = genTable.getSubTable().getColumns();
//            addDicts(dicts, subColumns);
//        }
//        return StringUtils.join(dicts, ", ");
//    }

//    /**
//     * 添加字典列表
//     *
//     * @param dicts   字典列表
//     * @param columns 列集合
//     */
//    public static void addDicts(Set<String> dicts, List<GenTableColumn> columns) {
//        for( GenTableColumn column : columns ) {
//            if( !column.isSuperColumn() && StringUtils.isNotEmpty(column.getDictType()) && StringUtils.equalsAny(
//                    column.getHtmlType(),
//                    new String[]{GenConstants.HTML_SELECT, GenConstants.HTML_RADIO, GenConstants.HTML_CHECKBOX}) ) {
//                dicts.add("'" + column.getDictType() + "'");
//            }
//        }
//    }

    /**
     * 获取权限前缀
     *
     * @param moduleName   模块名称
     * @param businessName 业务名称
     * @return 返回权限前缀
     */
    public static String getPermissionPrefix(String moduleName, String businessName) {
        return MessageFormat.format("{0}:{1}", moduleName, businessName);
    }

    /**
     * 获取上级菜单ID字段
     *
     * @return 上级菜单ID字段
     */
    public static String getParentMenuId(GenEntity genEntity) {
        return Optional.ofNullable(genEntity.getParentMenuId()).orElse(DEFAULT_PARENT_MENU_ID);
    }

//    /**
//     * 获取树编码
//     *
//     * @param paramsObj 生成其他选项
//     * @return 树编码
//     */
//    public static String getTreecode(JSONObject paramsObj) {
//        if( paramsObj.containsKey(GenConstants.TREE_CODE) ) {
//            return StringUtils.toCamelCase(paramsObj.getString(GenConstants.TREE_CODE));
//        }
//        return StringUtils.EMPTY;
//    }

//    /**
//     * 获取树父编码
//     *
//     * @param paramsObj 生成其他选项
//     * @return 树父编码
//     */
//    public static String getTreeParentCode(JSONObject paramsObj) {
//        if( paramsObj.containsKey(GenConstants.TREE_PARENT_CODE) ) {
//            return StringUtils.toCamelCase(paramsObj.getString(GenConstants.TREE_PARENT_CODE));
//        }
//        return StringUtils.EMPTY;
//    }

//    /**
//     * 获取树名称
//     *
//     * @param paramsObj 生成其他选项
//     * @return 树名称
//     */
//    public static String getTreeName(JSONObject paramsObj) {
//        if( paramsObj.containsKey(GenConstants.TREE_NAME) ) {
//            return StringUtils.toCamelCase(paramsObj.getString(GenConstants.TREE_NAME));
//        }
//        return StringUtils.EMPTY;
//    }

//    /**
//     * 获取需要在哪一列上面显示展开按钮
//     *
//     * @param genTable 业务表对象
//     * @return 展开按钮列序号
//     */
//    public static int getExpandColumn(GenTable genTable) {
//        String options = genTable.getOptions();
//        JSONObject paramsObj = JSON.parseObject(options);
//        String treeName = paramsObj.getString(GenConstants.TREE_NAME);
//        int num = 0;
//        for( GenTableColumn column : genTable.getColumns() ) {
//            if( column.isList() ) {
//                num++;
//                String columnName = column.getColumnName();
//                if( columnName.equals(treeName) ) {
//                    break;
//                }
//            }
//        }
//        return num;
//    }
}
