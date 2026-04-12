package com.kiwi.project.ai.mcp;

import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描所有带 {@link Controller} 的 Bean（含 {@code @RestController}），若方法上存在 {@link Tool}
 * 则注册为 {@link ToolCallbackProvider}，
 * 与 MCP Server、{@link org.springframework.ai.chat.client.ChatClient} 共用。
 */
@Configuration
public class KiwiControllerToolCallbackConfiguration {

    @Bean
    public ToolCallbackProvider kiwiControllerToolCallbackProvider(@NonNull ApplicationContext applicationContext) {
        List<Object> beans = new ArrayList<>();
        for (String beanName : applicationContext.getBeanNamesForAnnotation(Controller.class)) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null || !hasToolAnnotatedMethod(type)) {
                continue;
            }
            beans.add(applicationContext.getBean(beanName));
        }
        if (beans.isEmpty()) {
            return new StaticToolCallbackProvider(List.of());
        }
        return MethodToolCallbackProvider.builder().toolObjects(beans.toArray()).build();
    }

    /**
     * 仅用类型判断，避免对无 {@link Tool} 的 Controller（如 {@code AiChatCtl}）过早 {@code getBean}，
     * 否则会在构建 {@link ToolCallbackProvider} 时与 {@link org.springframework.ai.chat.model.ChatModel} 初始化形成环依赖。
     */
    private static boolean hasToolAnnotatedMethod(Class<?> clazz) {
        for (Class<?> c = ClassUtils.getUserClass(clazz); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (AnnotationUtils.findAnnotation(method, Tool.class) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
