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
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描容器中全部 Spring Bean 的类型，若类层次上存在带 {@link Tool} 的方法则纳入
 * {@link MethodToolCallbackProvider}（含 {@code @RestController}、{@code @Service} 等），
 * 与 MCP Server、{@link org.springframework.ai.chat.client.ChatClient} 共用。
 * <p>
 * 仅对「类型上能解析到 {@link Tool} 方法」的 Bean 调用 {@code getBean}，避免无谓初始化及与
 * {@link org.springframework.ai.chat.model.ChatModel} 等形成的环依赖。
 */
@Configuration
public class KiwiToolCallbackConfiguration {

    @Bean
    public ToolCallbackProvider kiwiToolCallbackProvider(@NonNull ApplicationContext applicationContext) {
        List<Object> beans = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null
                    || ToolCallbackProvider.class.isAssignableFrom(type)
                    || !hasToolAnnotatedMethod(type)) {
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
     * 仅用类型判断，避免对无 {@link Tool} 的 Bean 过早 {@code getBean}，
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
