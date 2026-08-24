package com.kiwi.project.ai;

import com.kiwi.project.ai.mcp.KiwiAdminAiMcpConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局助手对话：基于统一 {@link ChatClient}（{@code kiwiChatClient}）与 MCP 工具。
 * 前端动作由 {@link AssistantClientActionContext} 收集（如页面跳转）。
 * BPM 设计器改图请走 {@code /bpm/designer-agent/**}。
 */
@Slf4j
@Service
public class AiAssistantService {

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

        assistantClientActionContext.beginRequest();
        String content = kiwiAssistantChatClientProvider.getObject()
                .prompt()
                .system(KiwiAdminAiMcpConfiguration.SYSTEM_PROMPT)
                .messages(springMessages)
                .call()
                .content();
        List<ClientAction> actions = assistantClientActionContext.drainActions();

        if (content == null || content.isBlank()) {
            content = "（模型未返回文本，请重试。）";
        }

        AiAssistantResponse out = new AiAssistantResponse();
        out.setContent(content.trim());
        out.setActions(actions);
        return out;
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
