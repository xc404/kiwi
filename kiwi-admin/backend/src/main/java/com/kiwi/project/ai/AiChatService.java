package com.kiwi.project.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiChatProperties properties;
    private final ObjectMapper objectMapper;

    public String complete(List<AiChatMessage> messages) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI 对话未启用（kiwi.ai.enabled=false）");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 kiwi.ai.api-key（或环境变量 KIWI_AI_API_KEY）");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient client = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                .build();

        List<Map<String, String>> payloadMessages = new ArrayList<>();
        for (AiChatMessage m : messages) {
            if (m.getRole() == null || m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("role", m.getRole().trim());
            row.put("content", m.getContent());
            payloadMessages.add(row);
        }
        if (payloadMessages.isEmpty()) {
            throw new IllegalArgumentException("没有有效的对话内容");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", payloadMessages);

        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode err = root.path("error");
            if (!err.isMissingNode() && err.path("message").isTextual()) {
                throw new IllegalStateException(err.path("message").asText());
            }
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText();
            }
            throw new IllegalStateException("模型返回格式异常，未解析到 choices[0].message.content");
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            try {
                JsonNode n = objectMapper.readTree(detail);
                if (n.path("error").path("message").isTextual()) {
                    throw new IllegalStateException(n.path("error").path("message").asText());
                }
            } catch (Exception ignored) {
                // ignore
            }
            throw new IllegalStateException("上游返回错误 HTTP " + e.getStatusCode().value() + ": " + detail);
        } catch (RestClientException e) {
            throw new IllegalStateException("调用模型失败: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析模型响应失败: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        String s = base.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
