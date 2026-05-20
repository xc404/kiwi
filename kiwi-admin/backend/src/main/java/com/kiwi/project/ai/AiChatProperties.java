package com.kiwi.project.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务开关；模型连接参数见 {@code spring.ai.dashscope.*}（阿里云 DashScope / 通义）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai")
public class AiChatProperties {

    /**
     * 是否启用 AI 对话接口；关闭时请求会失败并提示。
     */
    private boolean enabled = true;

    /** 单会话最多保留的 user/assistant 消息条数 */
    private int conversationMaxMessages = 200;

    /** 单条消息 content 最大字符数（超出截断） */
    private int conversationMaxContentLength = 32_000;
}
