package com.kiwi.project.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务开关；模型连接参数见 {@code spring.ai.openai.*}。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai")
public class AiChatProperties {

    /**
     * 是否启用 AI 对话接口；关闭时请求会失败并提示。
     */
    private boolean enabled = true;
}
