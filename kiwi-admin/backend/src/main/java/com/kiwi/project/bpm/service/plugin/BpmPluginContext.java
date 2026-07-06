package com.kiwi.project.bpm.service.plugin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URLClassLoader;
import java.util.List;

/**
 * 单个插件 JAR 的运行时上下文：插件 ClassLoader、子 Spring 上下文及已桥接至宿主的 delegate bean 名。
 */
@Getter
@RequiredArgsConstructor
public class BpmPluginContext {

    private final String jarFileName;
    private final URLClassLoader classLoader;
    private final ConfigurableApplicationContext applicationContext;
    private final List<String> bridgedBeanNames;

    public void close() {
        applicationContext.close();
        try {
            classLoader.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
