package com.kiwi.bpmn.component.payment.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.kiwi.bpmn.component.payment.model.PaymentCreateRequest;
import com.kiwi.bpmn.component.payment.model.PaymentCreateResult;
import com.kiwi.bpmn.component.payment.model.PaymentQueryRequest;
import com.kiwi.bpmn.component.payment.model.PaymentQueryResult;
import org.springframework.stereotype.Component;

@Component
public class AlipayChannelAdapter {

    private static final String DefaultGateway =
            "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public PaymentCreateResult create(PaymentCreateRequest request) {
        String gateway = blankToDefault(request.alipayGatewayUrl(), DefaultGateway);
        String amountYuan = formatYuan(request.amountFen());
        String bizContent = "{\"out_trade_no\":\""
                + escapeJson(request.outTradeNo())
                + "\",\"total_amount\":\""
                + amountYuan
                + "\",\"subject\":\""
                + escapeJson(request.subject())
                + "\"}";
        Map<String, String> params = baseParams(request.alipayAppId(), "alipay.trade.precreate", bizContent);
        params.put("sign", AlipaySignHelper.signRsa2(params, request.alipayPrivateKey()));
        String body = postForm(gateway, params);
        return parseCreateResponse(body);
    }

    public PaymentQueryResult query(PaymentQueryRequest request) {
        String gateway = blankToDefault(request.alipayGatewayUrl(), DefaultGateway);
        String bizContent = "{\"out_trade_no\":\"" + escapeJson(request.outTradeNo()) + "\"}";
        Map<String, String> params = baseParams(request.alipayAppId(), "alipay.trade.query", bizContent);
        params.put("sign", AlipaySignHelper.signRsa2(params, request.alipayPrivateKey()));
        String body = postForm(gateway, params);
        return parseQueryResponse(body);
    }

    private Map<String, String> baseParams(String appId, String method, String bizContent) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", appId);
        params.put("method", method);
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", AlipaySignHelper.currentTimestamp());
        params.put("version", "1.0");
        params.put("biz_content", bizContent);
        return params;
    }

    private String postForm(String gateway, Map<String, String> params) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(gateway))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            AlipaySignHelper.buildFormBody(params), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("支付宝 HTTP 失败: " + response.statusCode());
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用支付宝失败: " + e.getMessage(), e);
        }
    }

    PaymentCreateResult parseCreateResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode responseNode = root.path("alipay_trade_precreate_response");
            String code = responseNode.path("code").asText();
            if (!"10000".equals(code)) {
                throw new IllegalStateException(
                        "支付宝下单失败: " + responseNode.path("sub_msg").asText(code));
            }
            String qrCode = responseNode.path("qr_code").asText("");
            return new PaymentCreateResult(body, qrCode, body);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析支付宝下单响应失败: " + e.getMessage(), e);
        }
    }

    PaymentQueryResult parseQueryResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode responseNode = root.path("alipay_trade_query_response");
            String code = responseNode.path("code").asText();
            if (!"10000".equals(code)) {
                throw new IllegalStateException(
                        "支付宝查单失败: " + responseNode.path("sub_msg").asText(code));
            }
            String tradeStatus = responseNode.path("trade_status").asText("UNKNOWN");
            String tradeNo = responseNode.path("trade_no").asText("");
            return new PaymentQueryResult(mapAlipayStatus(tradeStatus), tradeNo, body);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析支付宝查单响应失败: " + e.getMessage(), e);
        }
    }

    String mapAlipayStatus(String tradeStatus) {
        return switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> "SUCCESS";
            case "WAIT_BUYER_PAY" -> "PENDING";
            case "TRADE_CLOSED" -> "CLOSED";
            default -> tradeStatus;
        };
    }

    private String formatYuan(long amountFen) {
        long yuan = amountFen / 100;
        long fen = amountFen % 100;
        return yuan + "." + (fen < 10 ? "0" + fen : fen);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
