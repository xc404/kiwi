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
    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            业务工具名称带前缀（如 dict_*、user_*、bpmPd_*、assistant_navigate）；需要打开某管理后台页面时，先调用 auth_menus 获取当前用户可见菜单树，使用其中与侧栏一致的 path（如 /default/system/dict）作为 routePath，再调用 assistant_navigate；若目标页需要查询参数（如字典页 groupCode），传入 queryParamsJson（JSON 对象字符串）。
            若信息不足，先向用户追问，不要随意调用工具。
            """;

    @Bean
    public ChatClient kiwiAssistantChatClient(
            ChatModel chatModel,
            ToolCallbackProvider kiwiToolCallbackProvider) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolCallbacks(kiwiToolCallbackProvider)
                .build();
    }
}
