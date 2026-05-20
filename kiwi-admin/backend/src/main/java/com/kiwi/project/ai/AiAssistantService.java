package com.kiwi.project.ai;

import com.kiwi.project.ai.mcp.KiwiAdminAiMcpConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 助手对话：基于统一 {@link ChatClient}（{@code kiwiChatClient}）与 MCP 工具；由模型自行选用工具。
 * 前端动作由 {@link AssistantClientActionContext} 收集（菜单跳转、BPM 设计器建议等）。
 */
@Service
public class AiAssistantService {

    private static final String TOOL_ASSISTANT_DESIGNER_BPMN_XML = "assistant_designer_bpmn_xml";

    /** BPM 设计器：模型声称已改图但 actions 为空时的常见措辞 */
    private static final Pattern BPM_DESIGNER_FALSE_SUCCESS = Pattern.compile(
            "已成功|成功完成|已更新|已复制|已成功移除|已移除|已删除|精准复制|画布.*已|节点.*已.*更新|仅剩");

    /** 用户消息中的改图意图（非纯问答） */
    private static final Pattern BPM_DESIGNER_USER_EDIT_INTENT = Pattern.compile(
            "移除|删除|去掉|添加|追加|复制|修改|更新|改|连接|部署|导出|保存|插入|替换");

    /** 需先拉取它流程 BPMN 再改图（补救轮不强制只调 bpmn_xml） */
    private static final Pattern BPM_DESIGNER_NEEDS_SOURCE_PROCESS = Pattern.compile(
            "复制|从.{0,30}流程|源流程|其它流程|其他流程|bpmPd|CryoEMS|cryoems", Pattern.CASE_INSENSITIVE);

    private static final String BPM_DESIGNER_RETRY_USER = """
            上轮未登记任何画布动作（actions 为空）。用户请求的是修改 BPMN 流程图。
            请根据 system 中的「当前 BPMN XML」完成编辑，并必须调用 assistant_designer_bpmn_xml 提交完整 definitions XML；
            不要只在文本中描述已删除/已修改/已成功。若需从其它流程复制配置，可先 bpmPd_get 再 assistant_designer_bpmn_xml。
            若仅追加组件则用 assistant_designer_match_component。""";

    private final ObjectProvider<ChatClient> kiwiAssistantChatClientProvider;
    private final AiChatProperties properties;
    private final AssistantClientActionContext assistantClientActionContext;

    public AiAssistantService(
            @Qualifier("kiwiChatClient") ObjectProvider<ChatClient> kiwiAssistantChatClientProvider,
            AiChatProperties properties,
            AssistantClientActionContext assistantClientActionContext) {
        this.kiwiAssistantChatClientProvider = kiwiAssistantChatClientProvider;
        this.properties = properties;
        this.assistantClientActionContext = assistantClientActionContext;
    }

    public AiAssistantResponse run(List<AiChatMessage> messages) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI 对话未启用（kiwi.ai.enabled=false）");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }

        List<Message> springMessages = new ArrayList<>();
        for (AiChatMessage m : messages) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            springMessages.add(toSpringMessage(m));
        }
        if (springMessages.isEmpty()) {
            throw new IllegalArgumentException("没有有效的对话内容");
        }

        boolean bpmDesignerSession = isBpmDesignerSession(springMessages);
        String systemPrompt = KiwiAdminAiMcpConfiguration.SYSTEM_PROMPT;
        if (bpmDesignerSession) {
            systemPrompt = systemPrompt + "\n\n" + KiwiAdminAiMcpConfiguration.BPM_DESIGNER_SUPPLEMENT;
        }

        assistantClientActionContext.beginRequest();
        String content = callAssistant(systemPrompt, springMessages, null);
        List<ClientAction> actions = assistantClientActionContext.drainActions();

        if (bpmDesignerSession && actions.isEmpty() && shouldRetryBpmDesignerEdit(springMessages, content)) {
            List<Message> retryMessages = new ArrayList<>(springMessages);
            retryMessages.add(new AssistantMessage(content != null ? content : ""));
            retryMessages.add(new UserMessage(BPM_DESIGNER_RETRY_USER));

            ChatOptions retryOptions = null;
            String lastUser = lastUserMessageText(springMessages);
            if (lastUser != null && !BPM_DESIGNER_NEEDS_SOURCE_PROCESS.matcher(lastUser).find()) {
                retryOptions = ToolCallingChatOptions.builder()
                        .build();
            }

            assistantClientActionContext.beginRequest();
            String retryContent = callAssistant(systemPrompt, retryMessages, retryOptions);
            List<ClientAction> retryActions = assistantClientActionContext.drainActions();
            if (!retryActions.isEmpty()) {
                content = retryContent;
                actions = retryActions;
            }
        }

        if (content == null || content.isBlank()) {
            content = "（模型未返回文本，请重试。）";
        }

        AiAssistantResponse out = new AiAssistantResponse();
        out.setContent(content.trim());
        out.setActions(actions);
        appendBpmDesignerActionWarningIfNeeded(out, bpmDesignerSession, actions);
        return out;
    }

    private String callAssistant(String systemPrompt, List<Message> messages, @Nullable ChatOptions options) {
        var spec = kiwiAssistantChatClientProvider.getObject()
                .prompt()
                .system(systemPrompt)
                .messages(messages);
        if (options != null) {
            spec = spec.options(options);
        }
        return spec.call().content();
    }

    private static Map<String, Object> forcedBpmnXmlToolChoice() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", TOOL_ASSISTANT_DESIGNER_BPMN_XML);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", "function");
        choice.put("function", function);
        return choice;
    }

    private static boolean shouldRetryBpmDesignerEdit(List<Message> messages, @Nullable String assistantContent) {
        String lastUser = lastUserMessageText(messages);
        if (lastUser != null && BPM_DESIGNER_USER_EDIT_INTENT.matcher(lastUser).find()) {
            return true;
        }
        return assistantContent != null && BPM_DESIGNER_FALSE_SUCCESS.matcher(assistantContent).find();
    }

    @Nullable
    private static String lastUserMessageText(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof UserMessage um) {
                return um.getText();
            }
        }
        return null;
    }

    private void appendBpmDesignerActionWarningIfNeeded(
            AiAssistantResponse out, boolean bpmDesignerSession, List<ClientAction> actions) {
        if (!bpmDesignerSession || actions == null || !actions.isEmpty()) {
            return;
        }
        String text = out.getContent();
        if (text == null || !BPM_DESIGNER_FALSE_SUCCESS.matcher(text).find()) {
            return;
        }
        out.setContent(text
                + "\n\n⚠️ 本轮未登记画布动作（actions 为空），流程图可能未实际变更。"
                + "请重试并确保调用 assistant_designer_bpmn_xml 登记完整 BPMN XML。");
    }

    private static boolean isBpmDesignerSession(List<Message> messages) {
        for (Message m : messages) {
            if (m instanceof SystemMessage sm) {
                String text = sm.getText();
                if (text != null && text.contains("BPM 流程设计器")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Message toSpringMessage(AiChatMessage m) {
        String role = m.getRole() == null ? "user" : m.getRole().trim().toLowerCase();
        String c = m.getContent();
        return switch (role) {
            case "system" -> new SystemMessage(c);
            case "assistant" -> new AssistantMessage(c);
            default -> new UserMessage(c);
        };
    }
}

