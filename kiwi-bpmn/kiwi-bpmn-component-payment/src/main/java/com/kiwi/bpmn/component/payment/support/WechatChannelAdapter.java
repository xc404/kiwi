package com.kiwi.bpmn.component.payment.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.kiwi.bpmn.component.payment.model.PaymentCreateRequest;
import com.kiwi.bpmn.component.payment.model.PaymentCreateResult;
import com.kiwi.bpmn.component.payment.model.PaymentQueryRequest;
import com.kiwi.bpmn.component.payment.model.PaymentQueryResult;
import org.springframework.stereotype.Component;

@Component
public class WechatChannelAdapter {

    private static final String ApiHost = "https://api.mch.weixin.qq.com";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public PaymentCreateResult create(PaymentCreateRequest request) {
        String path = "/v3/pay/transactions/native";
        String notifyUrl = request.notifyUrl() != null && !request.notifyUrl().isBlank()
                ? request.notifyUrl()
                : "https://example.com/notify";
        String body = "{"
                + "\"appid\":\"" + escapeJson(request.wechatAppId()) + "\","
                + "\"mchid\":\"" + escapeJson(request.wechatMchId()) + "\","
                + "\"description\":\"" + escapeJson(request.subject()) + "\","
                + "\"out_trade_no\":\"" + escapeJson(request.outTradeNo()) + "\","
                + "\"notify_url\":\"" + escapeJson(notifyUrl) + "\","
                + "\"amount\":{\"total\":" + request.amountFen() + ",\"currency\":\"CNY\"}"
                + "}";
        String responseBody = signedRequest("POST", path, body, request.wechatMchId(),
                request.wechatCertSerial(), request.wechatPrivateKey());
        return parseCreateResponse(responseBody);
    }

    public PaymentQueryResult query(PaymentQueryRequest request) {
        String path = "/v3/pay/transactions/out-trade-no/"
                + urlEncodePath(request.outTradeNo())
                + "?mchid="
                + urlEncodePath(request.wechatMchId());
        String responseBody = signedRequest("GET", path, "", request.wechatMchId(),
                request.wechatCertSerial(), request.wechatPrivateKey());
        return parseQueryResponse(responseBody);
    }

    private String signedRequest(
            String method, String path, String body, String mchId, String certSerial, String privateKey) {
        String authorization = WechatSignHelper.authorization(
                mchId, certSerial, privateKey, method, path, body);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ApiHost + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", authorization)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            if ("POST".equals(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "微信 HTTP 失败: " + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用微信支付失败: " + e.getMessage(), e);
        }
    }

    PaymentCreateResult parseCreateResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String codeUrl = root.path("code_url").asText("");
            return new PaymentCreateResult(body, codeUrl, body);
        } catch (Exception e) {
            throw new IllegalStateException("解析微信下单响应失败: " + e.getMessage(), e);
        }
    }

    PaymentQueryResult parseQueryResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String tradeState = root.path("trade_state").asText("UNKNOWN");
            String transactionId = root.path("transaction_id").asText("");
            return new PaymentQueryResult(mapWechatStatus(tradeState), transactionId, body);
        } catch (Exception e) {
            throw new IllegalStateException("解析微信查单响应失败: " + e.getMessage(), e);
        }
    }

    String mapWechatStatus(String tradeState) {
        return switch (tradeState) {
            case "SUCCESS" -> "SUCCESS";
            case "NOTPAY", "USERPAYING" -> "PENDING";
            case "CLOSED", "REVOKED", "PAYERROR" -> "CLOSED";
            default -> tradeState;
        };
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String urlEncodePath(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
