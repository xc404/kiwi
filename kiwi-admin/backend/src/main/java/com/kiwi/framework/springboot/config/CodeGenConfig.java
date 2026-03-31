package com.kiwi.framework.springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取代码生成相关配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "code.gen")
public class CodeGenConfig
{
    /**
     * 作者
     */
    public static String author;

    /**
     * 生成包路径
     */
    public static String packageName;

    /**
     * 自动去除表前缀
     */
    public static boolean autoRemovePre;

    /**
     * 表前缀
     */
    public static String tablePrefix;

    /**
     * 是否允许生成文件覆盖到本地（自定义路径）
     */
    public static boolean allowOverwrite;

    public static String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        CodeGenConfig.author = author;
    }

    public static String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        CodeGenConfig.packageName = packageName;
    }

    public static boolean getAutoRemovePre() {
        return autoRemovePre;
    }

    public void setAutoRemovePre(boolean autoRemovePre) {
        CodeGenConfig.autoRemovePre = autoRemovePre;
    }

    public static String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        CodeGenConfig.tablePrefix = tablePrefix;
    }

    public static boolean isAllowOverwrite() {
        return allowOverwrite;
    }

    public void setAllowOverwrite(boolean allowOverwrite) {
        CodeGenConfig.allowOverwrite = allowOverwrite;
    }
}
