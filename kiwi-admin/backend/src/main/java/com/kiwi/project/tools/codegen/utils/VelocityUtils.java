package com.kiwi.project.tools.codegen.utils;

import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.GenEnum;
import com.kiwi.project.tools.codegen.entity.GenField;
import com.kiwi.project.tools.codegen.entity.JavaType;
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
 */
public class VelocityUtils {

    private static final String ProjectPath = "main/java";
    private static final String MybatisPath = "main/resources/mybatis";
    private static final String FrontendPath = "frontend/src/app/pages";
    private static final String IntegrationPath = "integration";
    private static final String DefaultParentMenuId = "system";

    public static VelocityContext prepareContext(CodeGenVo codeGenVo) {
        GenEntity genEntity = codeGenVo.getGenEntity();
        List<GenField> fields = codeGenVo.getFields();
        String moduleName = genEntity.getModuleName();
        String businessName = genEntity.getBusinessName();
        String packageName = genEntity.getPackageName();
        String functionName = genEntity.getFunctionName();
        GenEnum.DaoTpl daoTpl = genEntity.getDaoTpl() != null ? genEntity.getDaoTpl() : GenEnum.DaoTpl.MongoDB;

        GenField pkField = fields.stream().filter(GenField::isPk).findFirst().orElse(null);

        List<GenField> entityFields = fields.stream()
                .filter(f -> !GenField.isSuperColumn(f.getJavaField()))
                .toList();

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("tplCategory", genEntity.getGenTpl().name());
        velocityContext.put("daoTpl", daoTpl.name());
        velocityContext.put("tableName", genEntity.getTableName());
        velocityContext.put("functionName", StringUtils.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
        velocityContext.put("ClassName", genEntity.getClassName());
        velocityContext.put("className", StringUtils.uncapitalize(genEntity.getClassName()));
        velocityContext.put("moduleName", moduleName);
        velocityContext.put("BusinessName", StringUtils.capitalize(businessName));
        velocityContext.put("businessName", businessName);
        velocityContext.put("basePackage", getPackagePrefix(packageName));
        velocityContext.put("packageName", packageName);
        velocityContext.put("author", genEntity.getFunctionAuthor());
        velocityContext.put("datetime", new Date());
        velocityContext.put("pkField", pkField);
        velocityContext.put("importList", getImportList(codeGenVo));
        velocityContext.put("permissionPrefix", getPermissionPrefix(moduleName, businessName));
        velocityContext.put("parentMenuId", getParentMenuId(genEntity));
        velocityContext.put("menuId", moduleName + "_" + businessName);
        velocityContext.put("fields", fields);
        velocityContext.put("entityFields", entityFields);
        velocityContext.put("entity", genEntity);
        return velocityContext;
    }

    public static List<String> getTemplateList(GenEntity genEntity) {
        List<String> templates = new ArrayList<>();
        GenEnum.DaoTpl daoTpl = genEntity.getDaoTpl() != null ? genEntity.getDaoTpl() : GenEnum.DaoTpl.MongoDB;

        if (daoTpl == GenEnum.DaoTpl.MongoDB) {
            templates.add("vm/java/mongo/entity.java.vm");
            templates.add("vm/java/mongo/dao.java.vm");
            templates.add("vm/java/mongo/controller.java.vm");
        } else {
            templates.add("vm/java/mybatis/entity.java.vm");
            templates.add("vm/java/mybatis/mapper.java.vm");
            templates.add("vm/xml/mybatis/mapper.xml.vm");
            templates.add("vm/java/mybatis/controller.java.vm");
        }

        if (genEntity.getWebTpl() == GenEnum.WebTpl.Angular) {
            templates.add("vm/angular/component.ts.vm");
            templates.add("vm/angular/component.html.vm");
            templates.add("vm/angular/routing-snippet.ts.vm");
        }

        templates.add("vm/mongo/menu.json.vm");
        templates.add("vm/mongo/permissions.txt.vm");
        templates.add("vm/integration/readme.txt.vm");

        return templates;
    }

    public static String getFileName(String template, GenEntity genEntity) {
        String packageName = genEntity.getPackageName();
        String moduleName = genEntity.getModuleName();
        String className = genEntity.getClassName();
        String businessName = genEntity.getBusinessName();
        String javaPath = ProjectPath + "/" + StringUtils.replace(packageName, ".", "/");
        String mybatisPath = MybatisPath + "/" + moduleName;

        if (template.contains("mongo/entity.java")) {
            return MessageFormat.format("{0}/entity/{1}.java", javaPath, className);
        }
        if (template.contains("mongo/dao.java")) {
            return MessageFormat.format("{0}/dao/{1}Dao.java", javaPath, className);
        }
        if (template.contains("mongo/controller.java")) {
            return MessageFormat.format("{0}/ctl/{1}Ctl.java", javaPath, className);
        }
        if (template.contains("mybatis/entity.java")) {
            return MessageFormat.format("{0}/entity/{1}.java", javaPath, className);
        }
        if (template.contains("mybatis/mapper.java")) {
            return MessageFormat.format("{0}/mapper/{1}Mapper.java", javaPath, className);
        }
        if (template.contains("mybatis/controller.java")) {
            return MessageFormat.format("{0}/ctl/{1}Ctl.java", javaPath, className);
        }
        if (template.contains("mapper.xml")) {
            return MessageFormat.format("{0}/{1}Mapper.xml", mybatisPath, className);
        }
        if (template.contains("angular/component.ts")) {
            return MessageFormat.format("{0}/{1}/{2}.component.ts", FrontendPath, moduleName, businessName);
        }
        if (template.contains("angular/component.html")) {
            return MessageFormat.format("{0}/{1}/{2}.component.html", FrontendPath, moduleName, businessName);
        }
        if (template.contains("routing-snippet")) {
            return MessageFormat.format("{0}/routing-snippet.ts", IntegrationPath);
        }
        if (template.contains("menu.json")) {
            return MessageFormat.format("{0}/menu.json", IntegrationPath);
        }
        if (template.contains("permissions.txt")) {
            return MessageFormat.format("{0}/permissions.txt", IntegrationPath);
        }
        if (template.contains("readme.txt")) {
            return MessageFormat.format("{0}/README.txt", IntegrationPath);
        }
        return template;
    }

    public static String getPackagePrefix(String packageName) {
        int lastIndex = packageName.lastIndexOf(".");
        return StringUtils.substring(packageName, 0, lastIndex);
    }

    public static HashSet<String> getImportList(CodeGenVo codeGenVo) {
        List<GenField> columns = codeGenVo.getFields();
        HashSet<String> importList = new HashSet<>();
        for (GenField column : columns) {
            if (!GenField.isSuperColumn(column.getJavaField()) && JavaType.Date == column.getJavaType()) {
                importList.add("java.util.Date");
            } else if (!GenField.isSuperColumn(column.getJavaField()) && JavaType.BigDecimal == column.getJavaType()) {
                importList.add("java.math.BigDecimal");
            }
        }
        return importList;
    }

    public static String getPermissionPrefix(String moduleName, String businessName) {
        return MessageFormat.format("{0}:{1}", moduleName, businessName);
    }

    public static String getParentMenuId(GenEntity genEntity) {
        return Optional.ofNullable(genEntity.getParentMenuId()).filter(StringUtils::isNotBlank).orElse(DefaultParentMenuId);
    }
}
