package com.kiwi.project.ai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Kiwi 后台 AI 助手 {@link ChatClient}：工具经本机 MCP（{@link McpSyncClient}）与 Server2MCP 暴露的 MCP Server
 * 对齐，不在此注册全局 {@link org.springframework.ai.tool.ToolCallbackProvider}。
 */
@Configuration
public class KiwiAdminAiMcpConfiguration {
    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            工具名与 Swagger/OpenAPI 的 operationId 一致（如 auth_menus、dict_listDict、user_list、bpmPd_*、assistant_navigate 等）。
            需要打开某管理后台页面时，先调用 auth_menus 获取当前用户可见菜单树，使用其中与侧栏一致的 path（如 /system/dict）作为 routePath，再调用 assistant_navigate；若目标页需要查询参数（如字典页 groupCode），传入 queryParamsJson（JSON 对象字符串）。
            若信息不足，先向用户追问，不要随意调用工具。
            """;

    @Bean
    @Lazy
    public ChatClient kiwiAssistantChatClient(ChatModel chatModel, @Lazy McpSyncClient kiwiLocalMcpSyncClient) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(kiwiLocalMcpSyncClient))
                .build();
    }
}
