package com.kiwi.project.ai;

/**
 * 将 Spring AI / DashScope 等上游错误原文转为面向用户的中文提示。
 */
public final class AiModelErrorUtils {

    private AiModelErrorUtils() {
    }

    public static String toUserMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "大模型服务调用失败，请稍后重试。";
        }
        if (containsCode(rawMessage, "Arrearage")) {
            return "大模型服务账户欠费或余额不足，请充值阿里云 DashScope（百炼）后重试。";
        }
        if (containsCode(rawMessage, "InvalidApiKey")) {
            return "大模型 API Key 无效或未配置，请联系管理员检查配置。";
        }
        String upstream = extractJsonField(rawMessage, "message");
        if (upstream != null && !upstream.isBlank()) {
            return "大模型服务调用失败：" + upstream;
        }
        return "大模型服务调用失败，请稍后重试。";
    }

    private static boolean containsCode(String raw, String code) {
        return raw.contains("\"code\":\"" + code + "\"")
                || raw.contains("\"code\": \"" + code + "\"");
    }

    private static String extractJsonField(String raw, String field) {
        int jsonStart = raw.indexOf('{');
        if (jsonStart < 0) {
            return null;
        }
        String key = "\"" + field + "\"";
        int keyIdx = raw.indexOf(key, jsonStart);
        if (keyIdx < 0) {
            return null;
        }
        int colon = raw.indexOf(':', keyIdx + key.length());
        if (colon < 0) {
            return null;
        }
        int quoteStart = raw.indexOf('"', colon + 1);
        if (quoteStart < 0) {
            return null;
        }
        int quoteEnd = raw.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return null;
        }
        return raw.substring(quoteStart + 1, quoteEnd);
    }
}
