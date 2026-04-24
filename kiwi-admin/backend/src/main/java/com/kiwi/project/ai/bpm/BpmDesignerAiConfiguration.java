package com.kiwi.project.ai.bpm;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Arrays;

/**
 * BPM 设计器专用 {@link ChatClient}：工具来自本机 MCP，再按名称过滤子集。
 */
@Configuration
public class BpmDesignerAiConfiguration {

    @Bean
    @Qualifier("bpmDesignerChatClient")
    @Lazy
    public ChatClient bpmDesignerChatClient(ChatModel chatModel, @Lazy McpSyncClient kiwiLocalMcpSyncClient) {
        SyncMcpToolCallbackProvider all = new SyncMcpToolCallbackProvider(kiwiLocalMcpSyncClient);
        ToolCallbackProvider subset = () -> Arrays.stream(all.getToolCallbacks())
                .filter(tc -> {
                    String n = tc.getToolDefinition().name();
                    return n.startsWith("bpm_designer_") || "assistant_navigate".equals(n);
                })
                .toArray(ToolCallback[]::new);
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(subset)
                .build();
    }
}
