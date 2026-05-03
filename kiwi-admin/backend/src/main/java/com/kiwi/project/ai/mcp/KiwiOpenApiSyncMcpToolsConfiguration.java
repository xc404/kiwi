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
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 根据 {@link RestController} 上的 {@link Operation}（{@code summary} + {@code operationId}）构建
 * {@link MethodToolCallback}，再通过 {@link McpToolUtils#toSyncToolSpecifications(ToolCallback...)} 转为
 * MCP 的 {@link McpServerFeatures.SyncToolSpecification}，供 Spring AI MCP Server 使用。
 */
@Configuration
public class KiwiOpenApiSyncMcpToolsConfiguration {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> kiwiOpenApiMcpSyncTools(
            ApplicationContext applicationContext,
            ObjectMapper objectMapper) {
        ToolCallback[] callbacks = new OpenApiBasedToolCallbacks(applicationContext, objectMapper).getToolCallbacks();
        return McpToolUtils.toSyncToolSpecifications(callbacks);
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
