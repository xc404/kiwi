package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从 OpenAPI 3.x 或 Swagger 2.0（经解析器转为 OpenAPI 3 模型）文档为每个 HTTP 操作生成
 * 继承 {@code httpRequest} 父组件的 {@link BpmComponent} 草稿：执行行为由
 * {@link com.kiwi.bpmn.component.activity.HttpRequestActivity} 提供。
 */
public final class OpenApiComponentGenerator {

    private static final int MAX_SPEC_CHARS = 2_000_000;
    private static final Set<String> SUPPORTED_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

    private OpenApiComponentGenerator() {
    }

    /**
     * @param spec               JSON 或 YAML 全文
     * @param baseUrlOverride    非空时优先作为根 URL（用于 servers 为空或相对路径）
     * @param httpRequestParentId {@link com.kiwi.project.bpm.service.BpmComponentService#resolveHttpRequestParentComponentId()}
     */
    public static List<BpmComponent> buildComponents(
            String spec, String baseUrlOverride, String httpRequestParentId) {
        if (StringUtils.isBlank(spec)) {
            throw new IllegalArgumentException("spec 不能为空");
        }
        String trimmed = spec.trim();
        if (trimmed.length() > MAX_SPEC_CHARS) {
            throw new IllegalArgumentException("spec 过长（最大 " + MAX_SPEC_CHARS + " 字符）");
        }
        if (StringUtils.isBlank(httpRequestParentId)) {
            throw new IllegalArgumentException("httpRequestParentId 不能为空");
        }

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(trimmed, null, options);
        if (result.getOpenAPI() == null) {
            String msg =
                    result.getMessages() != null && !result.getMessages().isEmpty()
                            ? String.join("; ", result.getMessages())
                            : "未知错误";
            throw new IllegalArgumentException("无法解析 OpenAPI/Swagger 文档: " + msg);
        }
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new IllegalArgumentException("文档中未包含任何 paths");
        }

        String baseUrl = resolveBaseUrl(openAPI, baseUrlOverride);

        List<BpmComponent> out = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null) {
                continue;
            }
            Map<PathItem.HttpMethod, Operation> ops = pathItem.readOperationsMap();
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : ops.entrySet()) {
                String method = opEntry.getKey().name();
                if (!SUPPORTED_METHODS.contains(method)) {
                    continue;
                }
                Operation operation = opEntry.getValue();
                if (operation == null) {
                    continue;
                }
                out.add(
                        buildOneComponent(
                                openAPI, baseUrl, path, method, operation, httpRequestParentId));
            }
        }
        return out;
    }

    static String resolveBaseUrl(OpenAPI openAPI, String baseUrlOverride) {
        if (StringUtils.isNotBlank(baseUrlOverride)) {
            return baseUrlOverride.trim().replaceAll("/+$", "");
        }
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            Server s = openAPI.getServers().get(0);
            if (s != null && StringUtils.isNotBlank(s.getUrl())) {
                return s.getUrl().trim().replaceAll("/+$", "");
            }
        }
        return "";
    }

    private static BpmComponent buildOneComponent(
            OpenAPI openAPI,
            String baseUrl,
            String path,
            String method,
            Operation operation,
            String httpRequestParentId) {
        BpmComponent c = new BpmComponent();
        c.setParentId(httpRequestParentId);
        c.setType(BpmComponent.Type.SpringBean);
        c.setKey(buildKey(operation, path, method));
        c.setName(buildName(operation, path, method));
        c.setDescription(buildDescription(openAPI, path, method, operation));
        c.setGroup(firstTag(operation));
        c.setInputParameters(buildInputOverrides(baseUrl, path, method, operation));
        c.setOutputParameters(null);
        return c;
    }

    private static String buildKey(Operation operation, String path, String method) {
        String opId = operation.getOperationId();
        if (StringUtils.isNotBlank(opId)) {
            return "openapi_" + slug(opId);
        }
        return "openapi_" + method.toLowerCase(Locale.ROOT) + "_" + slug(path);
    }

    private static String buildName(Operation operation, String path, String method) {
        if (operation != null && StringUtils.isNotBlank(operation.getSummary())) {
            return operation.getSummary().trim();
        }
        if (operation != null && StringUtils.isNotBlank(operation.getOperationId())) {
            return operation.getOperationId();
        }
        return method + " " + path;
    }

    private static String firstTag(Operation operation) {
        if (operation != null
                && operation.getTags() != null
                && !operation.getTags().isEmpty()
                && StringUtils.isNotBlank(operation.getTags().get(0))) {
            return operation.getTags().get(0).trim();
        }
        return "OpenAPI";
    }

    private static String buildDescription(
            OpenAPI openAPI, String path, String method, Operation operation) {
        StringBuilder sb = new StringBuilder();
        if (operation != null && StringUtils.isNotBlank(operation.getDescription())) {
            sb.append(operation.getDescription().trim());
        } else if (operation != null && StringUtils.isNotBlank(operation.getSummary())) {
            sb.append(operation.getSummary().trim());
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("OpenAPI: ").append(method).append(" ").append(path);
        if (operation != null && StringUtils.isNotBlank(operation.getOperationId())) {
            sb.append("\noperationId: ").append(operation.getOperationId());
        }
        if (openAPI != null
                && openAPI.getInfo() != null
                && StringUtils.isNotBlank(openAPI.getInfo().getTitle())) {
            sb.append("\nAPI: ").append(openAPI.getInfo().getTitle());
            if (openAPI.getInfo().getVersion() != null) {
                sb.append(" ").append(openAPI.getInfo().getVersion());
            }
        }
        String params = describeParameters(operation);
        if (StringUtils.isNotBlank(params)) {
            sb.append("\n").append(params);
        }
        return sb.toString();
    }

    private static String describeParameters(Operation operation) {
        if (operation == null || operation.getParameters() == null) {
            return "";
        }
        return operation.getParameters().stream()
                .filter(p -> p != null && StringUtils.isNotBlank(p.getName()))
                .map(OpenApiComponentGenerator::oneParamLine)
                .collect(Collectors.joining("\n"));
    }

    private static String oneParamLine(Parameter p) {
        String in = p.getIn() != null ? p.getIn() : "?";
        String req = Boolean.TRUE.equals(p.getRequired()) ? "required" : "optional";
        String desc = StringUtils.isNotBlank(p.getDescription()) ? " — " + p.getDescription() : "";
        return "参数(" + in + ", " + req + "): " + p.getName() + desc;
    }

    private static final Pattern PATH_PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    /**
     * 将 OpenAPI 中的 path / query / header / body(schema 属性) 展开为独立输入参数，再用 JUEL 片段组合为
     * {@link com.kiwi.bpmn.component.activity.HttpRequestActivity} 所需的 {@code url}、{@code headers}、{@code body}。
     */
    private static List<BpmComponentParameter> buildInputOverrides(
            String baseUrl, String path, String method, Operation operation) {
        List<BpmComponentParameter> list = new ArrayList<>();
        Set<String> usedKeys = new LinkedHashSet<>();

        list.add(buildMethodParameter(method));

        List<Parameter> pathDeclared = new ArrayList<>();
        List<Parameter> queryDeclared = new ArrayList<>();
        List<Parameter> headerDeclared = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter p : operation.getParameters()) {
                if (p == null || StringUtils.isBlank(p.getName()) || p.getIn() == null) {
                    continue;
                }
                switch (p.getIn()) {
                    case "path" -> pathDeclared.add(p);
                    case "query" -> queryDeclared.add(p);
                    case "header" -> headerDeclared.add(p);
                    default -> {
                        /* cookie 等暂不展开 */
                    }
                }
            }
        }
        for (String name : pathPlaceholderNames(path)) {
            if (pathDeclared.stream().noneMatch(pp -> name.equals(pp.getName()))) {
                Parameter synthetic = new Parameter();
                synthetic.setName(name);
                synthetic.setIn("path");
                synthetic.setRequired(true);
                synthetic.setDescription("路径参数 {" + name + "}");
                pathDeclared.add(synthetic);
            }
        }

        Map<String, String> pathKeyByName = new LinkedHashMap<>();
        for (Parameter p : pathDeclared) {
            String inputKey = uniquifyKey("path_" + slug(p.getName()), usedKeys);
            pathKeyByName.put(p.getName(), inputKey);
            list.add(buildPathQueryParameter(p, "Path", inputKey, true));
        }
        Map<String, String> queryKeyByName = new LinkedHashMap<>();
        for (Parameter p : queryDeclared) {
            String inputKey = uniquifyKey("query_" + slug(p.getName()), usedKeys);
            queryKeyByName.put(p.getName(), inputKey);
            list.add(
                    buildPathQueryParameter(
                            p,
                            "Query",
                            inputKey,
                            Boolean.TRUE.equals(p.getRequired())));
        }
        Map<String, String> hdrKeyByName = new LinkedHashMap<>();
        for (Parameter p : headerDeclared) {
            String inputKey = uniquifyKey("hdr_" + slug(p.getName()), usedKeys);
            hdrKeyByName.put(p.getName(), inputKey);
            list.add(buildHeaderParameter(p, inputKey));
        }

        Schema jsonBodySchema = resolveJsonRequestBodySchema(operation);
        List<String> requiredBodyProps =
                jsonBodySchema != null && jsonBodySchema.getRequired() != null
                        ? jsonBodySchema.getRequired()
                        : List.of();
        Map<String, String> bodyKeyByProp = new LinkedHashMap<>();
        if (jsonBodySchema != null
                && jsonBodySchema.getProperties() != null
                && !jsonBodySchema.getProperties().isEmpty()) {
            for (Map.Entry<String, Schema> e : jsonBodySchema.getProperties().entrySet()) {
                String prop = e.getKey();
                if (StringUtils.isBlank(prop)) {
                    continue;
                }
                String key = uniquifyKey("body_" + slug(prop), usedKeys);
                bodyKeyByProp.put(prop, key);
                Schema propSchema = e.getValue();
                BpmComponentParameter bp = new BpmComponentParameter();
                bp.setKey(key);
                bp.setName("body." + prop);
                bp.setDescription(
                        describeSchemaProp(prop, propSchema, requiredBodyProps.contains(prop)));
                bp.setHtmlType(jsonBodyHtmlType(propSchema));
                bp.setGroup("Body");
                bp.setImportant(requiredBodyProps.contains(prop));
                bp.setRequired(requiredBodyProps.contains(prop));
                list.add(bp);
            }
        }

        String urlTemplate =
                buildUrlTemplate(
                        baseUrl, path, pathDeclared, pathKeyByName, queryDeclared, queryKeyByName);
        list.add(
                buildHiddenHttpField(
                        "url",
                        "url",
                        "由 Path/Query 参数组成的请求地址（JUEL：${path_*} / ${query_*}）",
                        urlTemplate));

        String headersTemplate =
                buildHeadersTemplate(
                        headerDeclared, hdrKeyByName, jsonBodySchema != null, method);
        if (headersTemplate != null) {
            list.add(
                    buildHiddenHttpField(
                            "headers",
                            "headers",
                            "由 Header 参数组成的 JSON 对象字符串",
                            headersTemplate));
        }

        String bodyTemplate = buildBodyTemplate(jsonBodySchema, method, bodyKeyByProp);
        if (bodyTemplate != null) {
            list.add(
                    buildHiddenHttpField(
                            "body",
                            "body",
                            "由 Body 参数组成的请求体（GET/HEAD 时由执行器忽略）",
                            bodyTemplate));
        }

        return list;
    }

    private static BpmComponentParameter buildMethodParameter(String method) {
        BpmComponentParameter methodParam = new BpmComponentParameter();
        methodParam.setKey("method");
        methodParam.setName("method");
        methodParam.setDescription("HTTP 方法（来自 OpenAPI）");
        methodParam.setHtmlType("#text");
        methodParam.setGroup("HTTP");
        methodParam.setImportant(true);
        methodParam.setRequired(false);
        methodParam.setDefaultValue(method);
        return methodParam;
    }

    private static BpmComponentParameter buildHiddenHttpField(
            String key, String name, String description, String defaultValue) {
        BpmComponentParameter p = new BpmComponentParameter();
        p.setKey(key);
        p.setName(name);
        p.setDescription(description);
        p.setHtmlType("#text");
        p.setGroup("HTTP");
        p.setImportant(false);
        p.setRequired(false);
        p.setHidden(true);
        p.setDefaultValue(defaultValue);
        return p;
    }

    private static List<String> pathPlaceholderNames(String pathTemplate) {
        List<String> names = new ArrayList<>();
        if (pathTemplate == null) {
            return names;
        }
        Matcher m = PATH_PLACEHOLDER.matcher(pathTemplate);
        while (m.find()) {
            String n = m.group(1).trim();
            if (!n.isEmpty() && !names.contains(n)) {
                names.add(n);
            }
        }
        return names;
    }

    private static BpmComponentParameter buildPathQueryParameter(
            Parameter p, String group, String inputKey, boolean requiredDefault) {
        BpmComponentParameter bp = new BpmComponentParameter();
        bp.setKey(inputKey);
        bp.setName(p.getIn() + "." + p.getName());
        bp.setDescription(
                StringUtils.defaultIfBlank(
                        p.getDescription(),
                        "OpenAPI " + p.getIn() + " 参数: " + p.getName()));
        bp.setHtmlType("#text");
        bp.setGroup(group);
        bp.setImportant(requiredDefault || Boolean.TRUE.equals(p.getRequired()));
        bp.setRequired(Boolean.TRUE.equals(p.getRequired()) || requiredDefault);
        return bp;
    }

    private static BpmComponentParameter buildHeaderParameter(Parameter p, String inputKey) {
        BpmComponentParameter bp = new BpmComponentParameter();
        bp.setKey(inputKey);
        bp.setName("header." + p.getName());
        bp.setDescription(
                StringUtils.defaultIfBlank(
                        p.getDescription(), "HTTP 请求头: " + p.getName()));
        bp.setHtmlType("#text");
        bp.setGroup("Header");
        bp.setImportant(Boolean.TRUE.equals(p.getRequired()));
        bp.setRequired(Boolean.TRUE.equals(p.getRequired()));
        return bp;
    }

    private static String buildUrlTemplate(
            String baseUrl,
            String path,
            List<Parameter> pathParams,
            Map<String, String> pathKeyByName,
            List<Parameter> queryParams,
            Map<String, String> queryKeyByName) {
        String pathPart = path == null ? "" : path;
        for (Parameter p : pathParams) {
            if (p.getName() == null) {
                continue;
            }
            String var =
                    pathKeyByName.getOrDefault(p.getName(), "path_" + slug(p.getName()));
            pathPart = pathPart.replace("{" + p.getName() + "}", "${" + var + "}");
        }
        String joined = joinBaseAndPath(baseUrl, pathPart);
        if (queryParams.isEmpty()) {
            return joined;
        }
        StringBuilder q = new StringBuilder(joined);
        q.append(joined.contains("?") ? "&" : "?");
        boolean first = true;
        for (Parameter p : queryParams) {
            if (!first) {
                q.append("&");
            }
            first = false;
            String qKey = encodeQueryKey(p.getName());
            String qVar =
                    queryKeyByName.getOrDefault(p.getName(), "query_" + slug(p.getName()));
            q.append(qKey).append("=${").append(qVar).append("}");
        }
        return q.toString();
    }

    /** Query 键名中的 &、= 等需百分号编码，避免破坏 URL。 */
    private static String encodeQueryKey(String name) {
        if (name == null) {
            return "";
        }
        return java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String buildHeadersTemplate(
            List<Parameter> headerParams,
            Map<String, String> hdrKeyByName,
            boolean jsonRequestBody,
            String method) {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            if (headerParams.isEmpty()) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        boolean hasContentTypeHeader =
                headerParams.stream()
                        .anyMatch(h -> h.getName() != null && "content-type".equalsIgnoreCase(h.getName()));
        if (jsonRequestBody
                && !"GET".equals(method)
                && !"HEAD".equals(method)
                && !hasContentTypeHeader) {
            sb.append("\"Content-Type\":\"application/json\"");
            first = false;
        }
        for (Parameter p : headerParams) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            String hk =
                    hdrKeyByName.getOrDefault(p.getName(), "hdr_" + slug(p.getName()));
            sb.append("\"")
                    .append(escapeJsonStr(p.getName()))
                    .append("\":\"${")
                    .append(hk)
                    .append("}\"");
        }
        sb.append("}");
        if (first) {
            return null;
        }
        return sb.toString();
    }

    private static String buildBodyTemplate(
            Schema jsonBodySchema, String method, Map<String, String> bodyKeyByProp) {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return null;
        }
        if (jsonBodySchema == null) {
            return null;
        }
        if (jsonBodySchema.getProperties() != null && !jsonBodySchema.getProperties().isEmpty()) {
            List<String> required =
                    jsonBodySchema.getRequired() != null
                            ? jsonBodySchema.getRequired()
                            : List.of();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Schema> e : jsonBodySchema.getProperties().entrySet()) {
                String prop = e.getKey();
                if (StringUtils.isBlank(prop)) {
                    continue;
                }
                if (!first) {
                    sb.append(",");
                }
                first = false;
                String var =
                        bodyKeyByProp.getOrDefault(prop, "body_" + slug(prop));
                Schema s = e.getValue();
                sb.append("\"")
                        .append(escapeJsonStr(prop))
                        .append("\":")
                        .append(bodyFragmentForSchema(s, var, required.contains(prop)));
            }
            sb.append("}");
            return sb.toString();
        }
        return "{}";
    }

    private static String bodyFragmentForSchema(Schema s, String varName, boolean required) {
        if (s == null) {
            return "\"" + "${" + varName + "}\"";
        }
        String t = s.getType();
        if (t == null && s.get$ref() != null) {
            return "\"" + "${" + varName + "}\"";
        }
        if ("integer".equals(t) || "number".equals(t) || "boolean".equals(t)) {
            return "${" + varName + "}";
        }
        return "\"" + "${" + varName + "}\"";
    }

    private static String describeSchemaProp(String prop, Schema propSchema, boolean required) {
        String t = propSchema != null ? propSchema.getType() : null;
        String desc =
                propSchema != null ? propSchema.getDescription() : null;
        String base =
                "JSON 请求体属性: "
                        + prop
                        + (t != null ? " (" + t + ")" : "")
                        + (required ? "，必填" : "");
        if (StringUtils.isNotBlank(desc)) {
            return base + " — " + desc;
        }
        return base;
    }

    private static String jsonBodyHtmlType(Schema propSchema) {
        if (propSchema == null) {
            return "#text";
        }
        String t = propSchema.getType();
        if ("boolean".equals(t)) {
            return "CheckBox";
        }
        if ("integer".equals(t) || "number".equals(t)) {
            return "#text";
        }
        return "#text";
    }

    private static Schema resolveJsonRequestBodySchema(Operation operation) {
        if (!hasJsonRequestBody(operation)) {
            return null;
        }
        Content content = operation.getRequestBody().getContent();
        MediaType mt = content.get("application/json");
        if (mt == null) {
            mt = content.get("application/*+json");
        }
        return mt != null ? mt.getSchema() : null;
    }

    private static String escapeJsonStr(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String uniquifyKey(String base, Set<String> used) {
        String k = base;
        int n = 2;
        while (used.contains(k)) {
            k = base + "_" + n;
            n++;
        }
        used.add(k);
        return k;
    }

    private static boolean hasJsonRequestBody(Operation operation) {
        if (operation == null || operation.getRequestBody() == null) {
            return false;
        }
        Content content = operation.getRequestBody().getContent();
        if (content == null) {
            return false;
        }
        MediaType mt = content.get("application/json");
        if (mt == null) {
            mt = content.get("application/*+json");
        }
        return mt != null;
    }

    static String joinBaseAndPath(String baseUrl, String path) {
        String p = path == null ? "" : path;
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (StringUtils.isBlank(baseUrl)) {
            return p;
        }
        return baseUrl + p;
    }

    private static String slug(String raw) {
        if (raw == null) {
            return "op";
        }
        String s =
                raw.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
        return StringUtils.isBlank(s) ? "op" : s;
    }
}
