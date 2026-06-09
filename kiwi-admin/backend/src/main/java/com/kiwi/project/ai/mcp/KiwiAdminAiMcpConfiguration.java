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
 * {@code assistant_navigate}、{@code assistant_designer_*} 经 {@link KiwiAssistantInProcessToolsFactory}
 * 进程内执行，与 {@link com.kiwi.project.ai.AssistantClientActionContext} 同线程登记 actions。
 * 各场景在调用时在 {@code .prompt()} 上自行设置 system，不在此写死 defaultSystem。
 */
@Configuration
public class KiwiAdminAiMcpConfiguration {
    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            工具名与 Swagger/OpenAPI 的 operationId 一致（如 auth_menus、dict_listDict、user_list、bpmPd_*、assistant_navigate、
            assistant_designer_toolbar、assistant_designer_bpmn_xml、assistant_designer_match_component 等）。
            根据用户当前对话与附加上下文自行判断需调用哪些工具；不要由系统替你区分「菜单场景」或「BPM 设计器场景」。
            当用户正在讨论或编辑 BPMN 流程图、需要驱动画布、替换 XML、从组件库追加元素时，必须调用 assistant_designer_* 工具登记动作；
            禁止仅在回复文本中声称「已添加」「已连接」「已更新流程图」——未调用工具则 actions 为空，前端画布不会变化。
            用户要加组件时：根据 messages 中组件库列表为用户匹配 componentId，再调用 assistant_designer_match_component(componentId)；不要替用户决定画布锚点元素 id。
            需要打开某管理后台页面时，先调用 auth_menus 获取当前用户可见菜单树，使用其中与侧栏一致的 path（如 /system/dict）作为 routePath，再调用 assistant_navigate；若目标页需要查询参数（如字典页 groupCode），传入 queryParamsJson（JSON 对象字符串）。
            若信息不足，先向用户追问，不要随意调用工具。
            """;

    public static final String BPM_DESIGNER_SUPPLEMENT = """
            【BPM 设计器 — 必须遵守】
            用户要求改图时，你必须在本轮调用工具（assistant_designer_*），不能只写自然语言。
            能力分工（见 system 中「可用 toolbar 命令」列表）：
            - 仅 undo/redo/zoom/copy/paste/removeSelection/find/save/deploy/start/export/saveAsComponent 等：assistant_designer_toolbar；
            - 画布追加单个业务组件：assistant_designer_match_component(componentId)；
            - 其它一切改 BPMN 结构或业务配置（改参数、复制它流程配置、增删节点/连线、批量改名等）：
              以 system「当前 BPMN XML」为底稿编辑后，必须 assistant_designer_bpmn_xml(完整 definitions)；
              前端会自动 import 并保存到当前流程，无需再 bpmPd_save，也不要用 toolbar 的 save 代替改 XML。
            复制它流程节点配置：bpmPd_get 源 XML → 合并 extensionElements 到 selectedElementId → assistant_designer_bpmn_xml。
            删除/移除节点或组件（按名称、Activity id、componentId）：从 system「当前 BPMN XML」删除对应 serviceTask、sequenceFlow 及 BPMNDI，再 assistant_designer_bpmn_xml；禁止无工具时声称已删除/已移除。
            未调用 assistant_designer_* 时不得声称已修改或已保存；actions 为空则前端不会变。
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
