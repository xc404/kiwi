package com.kiwi.project.tools.codegen.utils;

import org.apache.velocity.app.Velocity;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * VelocityEngine工厂
 *
 * @author ruoyi
 */
public class VelocityInitializer
{
    /**
     * 初始化vm方法
     */
    public static void initVelocity() {
        Properties p = new Properties();
        try {
            // 加载classpath目录下的vm文件
            p.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
//            p.setProperty("velocity.uberspectors", "org.apache.velocity.util.introspection.StaticUberspector");
            // 定义字符集
            p.setProperty(Velocity.INPUT_ENCODING, StandardCharsets.UTF_8.displayName());
            // 初始化Velocity引擎，指定配置Properties
            Velocity.init(p);
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }
}
