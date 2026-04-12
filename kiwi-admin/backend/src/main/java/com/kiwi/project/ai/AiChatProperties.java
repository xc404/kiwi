package com.kiwi.project.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI 兼容 Chat Completions（/v1/chat/completions）配置。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai")
public class AiChatProperties {

    /**
     * 是否启用 AI 对话接口；关闭时前端仍可打开页面，但请求会失败并提示。
     */
    private boolean enabled = true;

    /**
     * API Key，建议使用环境变量 {@code KIWI_AI_API_KEY}，勿提交到仓库。
     */
    private String apiKey = "";

    /**
     * 基础地址，不含尾斜杠，例如 {@code https://api.openai.com/v1} 或兼容网关。
     */
    private String baseUrl = "https://api.openai.com/v1";

    private String model = "gpt-4o-mini";

    private Duration connectTimeout = Duration.ofSeconds(30);

    private Duration readTimeout = Duration.ofMinutes(3);
}
