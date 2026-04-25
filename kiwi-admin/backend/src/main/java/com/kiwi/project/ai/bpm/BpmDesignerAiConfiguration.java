package com.kiwi.project.ai.bpm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * BPM 设计器专用 {@link ChatClient}：仅负责对话，动作由模型按 JSON 协议返回。
 */
@Configuration
public class BpmDesignerAiConfiguration {

    @Bean
    @Qualifier("bpmDesignerChatClient")
    @Lazy
    public ChatClient bpmDesignerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
