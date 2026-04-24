package com.cryo.integration.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 仅负责与 Kiwi-admin 的机机 HTTP 交互（启动流程、查询实例状态），与具体业务无关。
 * 并发与上限以 Kiwi 为准；429 时按配置重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiwiWorkflowClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KiwiWorkflowProperties properties;
    private HttpClient httpClient;

    private final Object startIntervalLock = new Object();
    private volatile long lastSuccessfulStartAtMillis;

    @PostConstruct
    void init() {
        KiwiWorkflowProperties.ClientProperties c = properties.getClient();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(c.getHttpConnectTimeoutSeconds()))
                .build();
    }

    /** 客户端已启用且具备 base-url、密钥（不含业务侧的 processDefinitionId）。 */
    public boolean isClientConfigured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getBaseUrl())
                && StringUtils.hasText(properties.getIntegrationSecret());
    }

    /**
     * 调用 Kiwi 启动 Camunda 流程实例（对应库中 {@code BpmProcess} 主键）。
     * 若返回 429，按 {@link KiwiWorkflowProperties.ClientProperties#getRateLimitRetryIntervalMillis()} 等待并重试，
     * 至多 {@link KiwiWorkflowProperties.ClientProperties#getMaxStartAttempts()} 次。
     */
    public String startProcess(String bpmProcessId, Map<String, Object> variables) throws Exception {
        if (!isClientConfigured()) {
            throw new IllegalStateException("Kiwi workflow client is not configured (enabled/baseUrl/secret)");
        }
        waitMinStartInterval();

        String base = trimTrailingSlash(properties.getBaseUrl());
        URI uri = URI.create(base + "/bpm/integration/process/" + bpmProcessId + "/start");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("variables", variables == null ? Map.of() : variables);
        String jsonBody = JSON.writeValueAsString(body);

        KiwiWorkflowProperties.ClientProperties c = properties.getClient();
        int maxAttempts = Math.max(1, c.getMaxStartAttempts());
        long retrySleepMs = Math.max(0L, c.getRateLimitRetryIntervalMillis());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(c.getHttpRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Kiwi-Integration-Secret", properties.getIntegrationSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();

            if (code == 429) {
                log.warn("Kiwi start returned 429 (attempt {}/{}), body: {}", attempt, maxAttempts, resp.body());
                if (attempt >= maxAttempts) {
                    throw new IllegalStateException(
                            "Kiwi start rate limited after " + maxAttempts + " attempts: " + resp.body());
                }
                if (retrySleepMs > 0) {
                    Thread.sleep(retrySleepMs);
                }
                continue;
            }

            if (code >= 200 && code < 300) {
                JsonNode root = JSON.readTree(resp.body());
                String instanceId = root.path("id").asText(null);
                if (!StringUtils.hasText(instanceId)) {
                    throw new IllegalStateException("Kiwi response missing process instance id: " + resp.body());
                }
                synchronized (startIntervalLock) {
                    lastSuccessfulStartAtMillis = System.currentTimeMillis();
                }
                return instanceId;
            }

            throw new IllegalStateException("Kiwi start workflow HTTP " + code + ": " + resp.body());
        }

        throw new IllegalStateException("Kiwi start: unexpected fallthrough");
    }

    /**
     * 查询流程实例状态；若 Kiwi 返回 404 则 {@link Optional#empty()}（实例尚不存在或已清理等）。
     */
    public Optional<KiwiProcessInstanceState> getProcessInstanceState(String instanceId) throws Exception {
        if (!isClientConfigured()) {
            throw new IllegalStateException("Kiwi workflow client is not configured");
        }
        String base = trimTrailingSlash(properties.getBaseUrl());
        URI uri = URI.create(base + "/bpm/integration/process-instances/" + instanceId + "/state");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getClient().getHttpRequestTimeoutSeconds()))
                .header("X-Kiwi-Integration-Secret", properties.getIntegrationSecret())
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 404) {
            return Optional.empty();
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Kiwi process instance state HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return Optional.of(JSON.readValue(resp.body(), KiwiProcessInstanceState.class));
    }

    private void waitMinStartInterval() throws InterruptedException {
        long min = properties.getClient().getMinIntervalMillisBetweenStarts();
        if (min <= 0) {
            return;
        }
        synchronized (startIntervalLock) {
            long elapsed = System.currentTimeMillis() - lastSuccessfulStartAtMillis;
            if (lastSuccessfulStartAtMillis > 0 && elapsed < min) {
                Thread.sleep(min - elapsed);
            }
        }
    }

    private static String trimTrailingSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
