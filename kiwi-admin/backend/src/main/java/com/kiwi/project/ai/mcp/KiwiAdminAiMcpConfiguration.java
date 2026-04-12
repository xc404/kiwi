package com.kiwi.project.ai.mcp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Kiwi 后台 AI 工具：
 * <ul>
 *   <li>{@link MethodToolCallbackProvider} — 供 {@link ChatClient} 与 Spring AI MCP Server 自动发现（见官方 MCP Server 文档）。</li>
 *   <li>{@link ChatClient} — 助手对话使用模型原生 tool-calling，不再解析自造 JSON。</li>
 * </ul>
 */
@Configuration
public class KiwiAdminAiMcpConfiguration {

    @Bean
    public ToolCallbackProvider kiwiAdminToolCallbackProvider(KiwiAdminAiTools kiwiAdminAiTools) {
        return MethodToolCallbackProvider.builder().toolObjects(kiwiAdminAiTools).build();
    }

    @Bean
    public ChatClient kiwiAssistantChatClient(
            ChatModel chatModel,
            ToolCallbackProvider kiwiAdminToolCallbackProvider,
            ToolCallbackProvider kiwiControllerToolCallbackProvider) {
        return ChatClient.builder(chatModel)
                .defaultSystem(KiwiAdminAiTools.SYSTEM_PROMPT)
                .defaultToolCallbacks(kiwiAdminToolCallbackProvider, kiwiControllerToolCallbackProvider)
                .build();
    }
}
