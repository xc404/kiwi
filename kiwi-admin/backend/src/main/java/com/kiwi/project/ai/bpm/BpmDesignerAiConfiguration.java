package com.kiwi.project.ai.bpm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BPM 设计器专用 {@link ChatClient}：仅绑定 {@link BpmDesignerTools}，避免与全局助手工具混杂。
 */
@Configuration
public class BpmDesignerAiConfiguration {

    @Bean
    @Qualifier("bpmDesignerChatClient")
    public ChatClient bpmDesignerChatClient(ChatModel chatModel, BpmDesignerTools bpmDesignerTools) {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(bpmDesignerTools)
                .build();
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(provider)
                .build();
    }
}
