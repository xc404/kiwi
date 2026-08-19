package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * BPM 设计器 Agent：独立 ChatClient（仅 MCP 发现/写库工具，不含 assistant_designer_*）。
 */
@Configuration
@EnableConfigurationProperties(DesignerAgentProperties.class)
public class DesignerAgentConfiguration {

    public static final String SystemPrompt = """
            你是 Kiwi BPM 设计器 Agent。通过 MCP 工具查询组件、流程、模板与市场插件。
            你的输出必须是 EditPlan JSON（见用户 prompt），禁止直接输出 BPMN XML。
            禁止调用 assistant_designer_* 工具。
            componentId 必须来自 bpmComp_aiPage 等工具查询结果，禁止臆造。
            信息不足时，在 summary 中说明需要用户补充的内容，operations 可为空数组。
            """;

    @Bean(name = "designerAgentChatClient")
    @Lazy
    public ChatClient designerAgentChatClient(
            ChatModel chatModel,
            @Lazy McpSyncClient kiwiLocalMcpSyncClient) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SystemPrompt)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(kiwiLocalMcpSyncClient))
                .defaultAdvisors(new DesignerAgentToolTraceAdvisor())
                .build();
    }
}
