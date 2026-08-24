package com.kiwi.project.ai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Kiwi 后台唯一 {@link ChatClient}：业务 OpenAPI 工具经本机 MCP（{@link McpSyncClient}）回环；
 * {@code assistant_navigate} 经 {@link KiwiAssistantInProcessToolsFactory}
 * 进程内执行，与 {@link com.kiwi.project.ai.AssistantClientActionContext} 同线程登记 actions。
 * 各场景在调用时在 {@code .prompt()} 上自行设置 system，不在此写死 defaultSystem。
 */
@Configuration
public class KiwiAdminAiMcpConfiguration {
    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            工具名与 Swagger/OpenAPI 的 operationId 一致（如 auth_menus、dict_listDict、user_list、bpmPd_*、assistant_navigate 等）。
            根据用户当前对话与附加上下文自行判断需调用哪些工具。
            BPM 流程图的增删改请引导用户使用设计器内的 Agent 面板（/bpm/designer-agent），不要声称已直接改画布。
            需要打开某管理后台页面时，先调用 auth_menus 获取当前用户可见菜单树，使用其中与侧栏一致的 path（如 /system/dict）作为 routePath，再调用 assistant_navigate；若目标页需要查询参数（如字典页 groupCode），传入 queryParamsJson（JSON 对象字符串）。
            若信息不足，先向用户追问，不要随意调用工具。
            """;

    @Bean(name = "kiwiChatClient")
    @Lazy
    public ChatClient kiwiChatClient(
            ChatModel chatModel,
            KiwiAssistantInProcessToolsFactory assistantInProcessToolsFactory,
            @Lazy McpSyncClient kiwiLocalMcpSyncClient) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(
                        assistantInProcessToolsFactory.createToolCallbackProvider(),
                        new SyncMcpToolCallbackProvider(kiwiLocalMcpSyncClient))
                .build();
    }
}
