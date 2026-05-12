package com.kiwi.project.ai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Kiwi 后台唯一 {@link ChatClient}：工具经本机 MCP（{@link McpSyncClient}）与
 * {@link KiwiOpenApiSyncMcpToolsConfiguration} 暴露的 MCP Server 对齐。
 * 各场景（菜单助手、BPM 设计器等）在调用时在 {@code .prompt()} 上自行设置 system，不在此写死 defaultSystem。
 */
@Configuration
public class KiwiAdminAiMcpConfiguration {
    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            工具名与 Swagger/OpenAPI 的 operationId 一致（如 auth_menus、dict_listDict、user_list、bpmPd_*、assistant_navigate、
            assistant_designer_toolbar、assistant_designer_bpmn_xml、assistant_designer_append_component 等）。
            根据用户当前对话与附加上下文自行判断需调用哪些工具；不要由系统替你区分「菜单场景」或「BPM 设计器场景」。
            当用户正在讨论或编辑 BPMN 流程图、需要驱动画布、替换 XML、从组件库追加元素时，使用 assistant_designer_* 工具登记将由前端执行的设计器动作。
            需要打开某管理后台页面时，先调用 auth_menus 获取当前用户可见菜单树，使用其中与侧栏一致的 path（如 /system/dict）作为 routePath，再调用 assistant_navigate；若目标页需要查询参数（如字典页 groupCode），传入 queryParamsJson（JSON 对象字符串）。
            若信息不足，先向用户追问，不要随意调用工具。
            """;

    @Bean(name = "kiwiChatClient")
    @Lazy
    public ChatClient kiwiChatClient(ChatModel chatModel, @Lazy McpSyncClient kiwiLocalMcpSyncClient) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(kiwiLocalMcpSyncClient))
                .build();
    }
}
