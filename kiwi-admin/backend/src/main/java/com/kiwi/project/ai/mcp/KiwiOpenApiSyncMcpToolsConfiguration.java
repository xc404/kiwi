package com.kiwi.project.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MCP 工具来源合并：
 * <ol>
 *   <li>各 {@link RestController} 上 {@link Operation}（{@code operationId} + {@code summary}）→ OpenAPI 扫描</li>
 *   <li>容器内 {@code com.kiwi.project} 包下、带 {@link Tool} 标注方法的 Spring bean（助手前端动作等），由 {@link MethodToolCallbackProvider} 生成回调</li>
 * </ol>
 * 再经 {@link McpToolUtils#toSyncToolSpecifications(ToolCallback...)} 转为 MCP 规格。
 */
@Configuration
public class KiwiOpenApiSyncMcpToolsConfiguration {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> kiwiOpenApiMcpSyncTools(
            ApplicationContext applicationContext,
            ObjectMapper objectMapper) {
        ToolCallback[] openapi = new OpenApiBasedToolCallbacks(applicationContext, objectMapper).getToolCallbacks();
        Object[] assistantBeans = collectBeansWithSpringAiToolMethods(applicationContext);
        ToolCallback[] assistant = assistantBeans.length == 0
                ? new ToolCallback[0]
                : MethodToolCallbackProvider.builder()
                        .toolObjects(assistantBeans)
                        .build()
                        .getToolCallbacks();
        ToolCallback[] merged = Stream.concat(Stream.of(openapi), Stream.of(assistant)).toArray(ToolCallback[]::new);
        return McpToolUtils.toSyncToolSpecifications(merged);
    }

    /**
     * {@link MethodToolCallbackProvider} 不接收 {@link ApplicationContext}；Spring AI 也未在 MCP 工具合并处提供「自动扫容器」的专用 API。
     * 此处按 {@link Tool} 元数据发现 bean，与 {@link OpenApiBasedToolCallbacks} 的扫描方式一致。
     */
    private Object[] collectBeansWithSpringAiToolMethods(ApplicationContext applicationContext) {
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

    private boolean hasSpringAiToolAnnotatedMethod(Class<?> userClass) {
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

    private static final class OpenApiBasedToolCallbacks implements ToolCallbackProvider {

        private static final Set<Class<?>> EXCLUDED_CONTROLLER_TYPES = Set.of(
                com.kiwi.project.ai.AiChatCtl.class
        );

        private final ApplicationContext applicationContext;
        private final ObjectMapper objectMapper;
        private volatile ToolCallback[] cached;

        OpenApiBasedToolCallbacks(@NonNull ApplicationContext applicationContext, @NonNull ObjectMapper objectMapper) {
            this.applicationContext = applicationContext;
            this.objectMapper = objectMapper;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (cached != null) {
                    return cached;
                }
                cached = buildCallbacks();
                return cached;
            }
        }

        private ToolCallback[] buildCallbacks() {
            List<ToolCallback> out = new ArrayList<>();
            Set<String> usedNames = new LinkedHashSet<>();
            for (String beanName : applicationContext.getBeanDefinitionNames()) {
                Class<?> type = applicationContext.getType(beanName);
                if (type == null || ToolCallbackProvider.class.isAssignableFrom(type)) {
                    continue;
                }
                if (AnnotationUtils.findAnnotation(type, RestController.class) == null) {
                    continue;
                }
                Class<?> userClass = ClassUtils.getUserClass(type);
                if (!userClass.getPackageName().startsWith("com.kiwi.project")) {
                    continue;
                }
                if (isExcludedController(userClass)) {
                    continue;
                }
                Object bean = applicationContext.getBean(beanName);
                for (Method method : collectCandidateMethods(userClass)) {
                    if (!Modifier.isPublic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                        continue;
                    }
                    Operation op = AnnotationUtils.findAnnotation(method, Operation.class);
                    if (op == null || !StringUtils.hasText(op.summary())) {
                        continue;
                    }
                    if (!hasMcpFriendlyParameters(method)) {
                        continue;
                    }
                    if (!StringUtils.hasText(op.operationId())) {
                        continue;
                    }
                    String toolName = op.operationId().trim();
                    if (!usedNames.add(toolName)) {
                        throw new IllegalStateException("重复的 MCP 工具 operationId: " + toolName);
                    }
                    String description = Stream.of(op.summary(), op.description())
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .collect(Collectors.joining("。"));
                    if (!StringUtils.hasText(description)) {
                        description = toolName;
                    }
                    ToolDefinition def = ToolDefinition.builder()
                            .name(toolName)
                            .description(description)
                            .inputSchema(buildInputSchema(method))
                            .build();
                    Method interfaceMethod = ClassUtils.getInterfaceMethodIfPossible(method);
                    Method m = interfaceMethod != null ? interfaceMethod : method;
                    out.add(MethodToolCallback.builder()
                            .toolDefinition(def)
                            .toolMethod(m)
                            .toolObject(bean)
                            .build());
                }
            }
            return out.toArray(ToolCallback[]::new);
        }

        private static boolean isExcludedController(Class<?> userClass) {
            if (EXCLUDED_CONTROLLER_TYPES.contains(userClass)) {
                return true;
            }
            String pkg = userClass.getPackageName();
            return pkg.contains(".integration.");
        }

        private static List<Method> collectCandidateMethods(Class<?> userClass) {
            List<Method> methods = new ArrayList<>();
            for (Class<?> c = userClass; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    methods.add(m);
                }
            }
            return methods;
        }

        private static boolean hasMcpFriendlyParameters(Method method) {
            for (Parameter p : method.getParameters()) {
                Class<?> t = p.getType();
                if (ToolContext.class.isAssignableFrom(t)) {
                    return false;
                }
                if (!isAllowedParameterType(t)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isAllowedParameterType(Class<?> t) {
            if (t.isPrimitive() || t.equals(String.class) || Number.class.isAssignableFrom(t)
                    || t.equals(Boolean.class) || t.equals(Character.class)) {
                return true;
            }
            if (t.isEnum()) {
                return true;
            }
            if (t.equals(Void.TYPE)) {
                return false;
            }
            String pkg = t.getPackageName();
            if (pkg.startsWith("jakarta.servlet") || pkg.startsWith("javax.servlet")) {
                return false;
            }
            if (pkg.startsWith("org.springframework.web.servlet") || pkg.startsWith("org.springframework.ui")) {
                return false;
            }
            if (pkg.startsWith("org.springframework.web.multipart")) {
                return false;
            }
            if (pkg.startsWith("org.springframework.validation")) {
                return false;
            }
            if (pkg.startsWith("org.springframework.security")) {
                return false;
            }
            if (t.getSimpleName().contains("HttpServlet") || t.getSimpleName().contains("HttpSession")) {
                return false;
            }
            return true;
        }

        private String buildInputSchema(Method method) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "object");
            ObjectNode props = objectMapper.createObjectNode();
            ArrayNode required = objectMapper.createArrayNode();
            for (Parameter p : method.getParameters()) {
                String pname = p.getName();
                props.set(pname, schemaNodeForJavaType(p.getType()));
            }
            root.set("properties", props);
            root.set("required", required);
            return root.toString();
        }

        private ObjectNode schemaNodeForJavaType(Class<?> t) {
            ObjectNode n = objectMapper.createObjectNode();
            if (t == int.class || t == Integer.class || t == long.class || t == Long.class
                    || t == short.class || t == Short.class || t == byte.class || t == Byte.class) {
                n.put("type", "integer");
                return n;
            }
            if (t == double.class || t == Double.class || t == float.class || t == Float.class) {
                n.put("type", "number");
                return n;
            }
            if (t == boolean.class || t == Boolean.class) {
                n.put("type", "boolean");
                return n;
            }
            if (t == String.class || t.isEnum()) {
                n.put("type", "string");
                return n;
            }
            if (List.class.isAssignableFrom(t) || t.isArray()) {
                n.put("type", "array");
                n.set("items", objectMapper.createObjectNode().put("type", "string"));
                return n;
            }
            if (java.util.Map.class.isAssignableFrom(t)) {
                n.put("type", "object");
                n.put("additionalProperties", true);
                return n;
            }
            n.put("type", "object");
            n.put("description", "JSON 对象，字段见接口文档或对应 Java DTO");
            return n;
        }
    }
}
