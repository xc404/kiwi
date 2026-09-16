package com.kiwi.bpmn.designer.harness;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class DesignerHarnessChatClientConfiguration {

    public static final String BeanName = "designerHarnessChatClient";

    @Bean(name = BeanName)
    @Lazy
    public ChatClient designerHarnessChatClient(ChatModel chatModel, DesignerHarnessTools tools) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(MethodToolCallbackProvider.builder().toolObjects(tools).build())
                .build();
    }
}