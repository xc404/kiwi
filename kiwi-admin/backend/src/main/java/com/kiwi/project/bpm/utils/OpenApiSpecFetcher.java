package com.kiwi.project.bpm.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 从 http(s) URL 拉取 OpenAPI / Swagger 文档正文（JSON 或 YAML 文本）。
 */
public final class OpenApiSpecFetcher {

    /** 与 {@link OpenApiComponentGenerator} 中 spec 全文上限一致 */
    static final int MAX_SPEC_CHARS = 2_000_000;

    private static final int CONNECT_TIMEOUT_SEC = 15;
    private static final int READ_TIMEOUT_SEC = 60;

    private OpenApiSpecFetcher() {
    }

    /**
     * GET 拉取 URL 响应体为 UTF-8 字符串；仅允许 {@code http}、{@code https}。
     *
     * @throws IllegalArgumentException 协议非法或正文超长
     * @throws IllegalStateException    非 2xx、IO 或中断
     */
    public static String fetch(String urlRaw) {
        if (StringUtils.isBlank(urlRaw)) {
            throw new IllegalArgumentException("specUrl 不能为空");
        }
        String trimmed = urlRaw.trim();
        URI uri = URI.create(trimmed);
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("specUrl 仅支持 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("specUrl 缺少主机名");
        }

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(READ_TIMEOUT_SEC))
                        .GET()
                        .header(
                                "Accept",
                                "application/json, application/yaml, text/yaml, application/vnd.oai.openapi, */*")
                        .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("拉取 OpenAPI 文档 IO 失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("拉取 OpenAPI 文档被中断", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("拉取 OpenAPI 文档失败，HTTP " + response.statusCode());
        }

        String body = response.body();
        if (body == null) {
            throw new IllegalStateException("响应体为空");
        }
        if (body.length() > MAX_SPEC_CHARS) {
            throw new IllegalArgumentException("文档过长（最大 " + MAX_SPEC_CHARS + " 字符）");
        }
        return body;
    }
}
