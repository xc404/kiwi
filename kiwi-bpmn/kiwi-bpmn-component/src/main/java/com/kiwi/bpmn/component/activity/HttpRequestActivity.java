package com.kiwi.bpmn.component.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiwi.common.utils.JsonUtils;
import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 同步 HTTP 客户端：使用 {@link java.net.http.HttpClient}，响应体按 UTF-8 解码为字符串。
 */
@ComponentDescription(
        name = "HTTP 请求",
        group = "HTTP",
        version = "1.0",
        description = "对指定 URL 发起 HTTP/HTTPS 请求；可将状态码、响应体、响应头（JSON）写入流程变量",
        inputs = {
                @ComponentParameter(
                        key = "url",
                        htmlType = "#text",
                        name = "url",
                        description = "绝对地址，仅支持 http 或 https",
                        required = true),
                @ComponentParameter(
                        key = "method",
                        htmlType = "#text",
                        name = "method",
                        description = "HTTP 方法：GET、HEAD、POST、PUT、PATCH、DELETE（默认 GET）",
                        schema = @Schema(defaultValue = "GET")),
                @ComponentParameter(
                        key = "headers",
                        htmlType = "#text",
                        name = "headers",
                        description = "可选，JSON 对象字符串，例如 {\"Content-Type\":\"application/json\"}"),
                @ComponentParameter(
                        key = "body",
                        htmlType = "#text",
                        name = "body",
                        description = "请求体字符串；GET/HEAD 忽略"),
                @ComponentParameter(
                        key = "connectTimeoutSeconds",
                        htmlType = "#text",
                        name = "connectTimeoutSeconds",
                        description = "连接超时（秒）",
                        schema = @Schema(defaultValue = "10")),
                @ComponentParameter(
                        key = "readTimeoutSeconds",
                        htmlType = "#text",
                        name = "readTimeoutSeconds",
                        description = "请求/响应超时（秒）",
                        schema = @Schema(defaultValue = "30"))
        },
        outputs = {
                @ComponentParameter(
                        key = "statusCode",
                        htmlType = "#text",
                        description = "写入 HTTP 状态码的流程变量名",
                        schema = @Schema(defaultValue = "statusCode")),
                @ComponentParameter(
                        key = "responseBody",
                        htmlType = "#text",
                        description = "写入响应体（UTF-8 字符串）的流程变量名",
                        schema = @Schema(defaultValue = "responseBody")),
                @ComponentParameter(
                        key = "responseHeaders",
                        htmlType = "#text",
                        description = "写入响应头 JSON 字符串的流程变量名（多值头为 JSON 数组）",
                        schema = @Schema(defaultValue = "responseHeaders"))
        })
@Component("httpRequest")
public class HttpRequestActivity extends AbstractBpmnActivityBehavior {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        String urlRaw = requireUrl(execution);
        URI uri = parseHttpUri(urlRaw);

        String method =
                ExecutionUtils.getStringInputVariable(execution, "method")
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toUpperCase(Locale.ROOT))
                        .orElse("GET");
        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        }

        int connectSec =
                ExecutionUtils.getIntInputVariable(execution, "connectTimeoutSeconds").orElse(10);
        int readSec = ExecutionUtils.getIntInputVariable(execution, "readTimeoutSeconds").orElse(30);
        if (connectSec <= 0 || readSec <= 0) {
            throw new IllegalArgumentException("connectTimeoutSeconds 与 readTimeoutSeconds 须为正整数");
        }

        Optional<String> headersJson = ExecutionUtils.getStringInputVariable(execution, "headers");
        Optional<String> bodyOpt = ExecutionUtils.getStringInputVariable(execution, "body");

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectSec))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

        HttpRequest.Builder reqBuilder =
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(readSec));
        applyHeaders(reqBuilder, headersJson);

        HttpRequest request = buildRequest(reqBuilder, method, bodyOpt);

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("HTTP 请求 IO 失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP 请求被中断", e);
        }

        String statusVar = ExecutionUtils.getOutputVariableName(execution, "statusCode");
        String bodyVar = ExecutionUtils.getOutputVariableName(execution, "responseBody");
        String headersVar = ExecutionUtils.getOutputVariableName(execution, "responseHeaders");

        if (statusVar != null) {
            execution.setVariable(statusVar, response.statusCode());
        }
        if (bodyVar != null) {
            execution.setVariable(bodyVar, response.body());
        }
        if (headersVar != null) {
            execution.setVariable(headersVar, responseHeadersToJson(response.headers()));
        }

        super.leave(execution);
    }

    private static String requireUrl(ActivityExecution execution) {
        return ExecutionUtils.getStringInputVariable(execution, "url")
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("url 不能为空"));
    }

    static URI parseHttpUri(String urlRaw) {
        URI uri = URI.create(urlRaw.trim());
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("url 须为 http 或 https 绝对地址");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("url 缺少主机名");
        }
        return uri;
    }

    static void applyHeaders(HttpRequest.Builder builder, Optional<String> headersJson) {
        if (headersJson.isEmpty() || headersJson.get().isBlank()) {
            return;
        }
        JsonNode root = parseHeadersDocument(headersJson.get());
        root.fields()
                .forEachRemaining(
                        e -> {
                            JsonNode node = e.getValue();
                            if (node == null || node.isNull()) {
                                return;
                            }
                            builder.header(e.getKey(), headerValueToString(node));
                        });
    }

    private static String headerValueToString(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    static JsonNode parseHeadersDocument(String headersJson) {
        try {
            JsonNode root = JsonUtils.readTree(headersJson.trim());
            if (!root.isObject()) {
                throw new IllegalArgumentException("headers 须为合法 JSON 对象");
            }
            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("headers 须为合法 JSON 对象: " + e.getMessage(), e);
        }
    }

    static HttpRequest buildRequest(
            HttpRequest.Builder reqBuilder, String method, Optional<String> bodyOpt) {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return reqBuilder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        String body = bodyOpt.orElse("");
        return reqBuilder
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    static String responseHeadersToJson(HttpHeaders headers) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        headers
                .map()
                .forEach(
                        (name, values) -> {
                            if (values.size() == 1) {
                                map.put(name, values.get(0));
                            } else {
                                map.put(name, values);
                            }
                        });
        try {
            return JsonUtils.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("响应头序列化为 JSON 失败: " + e.getMessage(), e);
        }
    }
}
