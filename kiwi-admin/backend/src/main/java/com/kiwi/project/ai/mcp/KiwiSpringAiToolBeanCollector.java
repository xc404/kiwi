package com.kiwi.project.ai.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 发现容器内带 Spring AI {@link Tool} 的 bean（如 {@code AssistantDesignerTools}），供进程内
 * {@link org.springframework.ai.tool.method.MethodToolCallbackProvider} 与 MCP 配置复用。
 */
public final class KiwiSpringAiToolBeanCollector {

    private KiwiSpringAiToolBeanCollector() {
    }

    public static Object[] collectAssistantToolBeans(ApplicationContext applicationContext) {
        List<Object> beans = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null || ToolCallbackProvider.class.isAssignableFrom(type)) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(type);
            if (!userClass.getPackageName().startsWith("com.kiwi.project")) {
                continue;
            }
            if (userClass.getPackageName().contains(".integration.")) {
                continue;
            }
            if (!hasSpringAiToolAnnotatedMethod(userClass)) {
                continue;
            }
            beans.add(applicationContext.getBean(beanName));
        }
        beans.sort(Comparator.comparing(b -> ClassUtils.getUserClass(b.getClass()).getName()));
        return beans.toArray();
    }

    private static boolean hasSpringAiToolAnnotatedMethod(Class<?> userClass) {
        for (Class<?> c = userClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                if (AnnotationUtils.findAnnotation(m, Tool.class) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
