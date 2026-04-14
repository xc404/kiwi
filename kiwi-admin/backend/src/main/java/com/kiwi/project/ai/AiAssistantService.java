package com.kiwi.project.ai;

import com.kiwi.project.system.ai.MenuAssistantActionContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手对话：基于 {@link ChatClient} 与 Spring AI 原生 tool-calling；菜单相关工具由 {@link com.kiwi.project.system.ai.MenuAssistantTools} 提供，
 * 并通过 MCP Server（WebMVC/SSE）对外暴露同一套 {@link org.springframework.ai.tool.ToolCallback}。
 */
@Service
public class AiAssistantService {

    private final ChatClient kiwiAssistantChatClient;
    private final AiChatProperties properties;
    private final MenuAssistantActionContext menuAssistantActionContext;

    public AiAssistantService(
            @Qualifier("kiwiAssistantChatClient") ChatClient kiwiAssistantChatClient,
            AiChatProperties properties,
            MenuAssistantActionContext menuAssistantActionContext) {
        this.kiwiAssistantChatClient = kiwiAssistantChatClient;
        this.properties = properties;
        this.menuAssistantActionContext = menuAssistantActionContext;
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

        menuAssistantActionContext.beginRequest();

        String content;
        List<AiAssistantResponse.ClientAction> actions;
        try {
            content = kiwiAssistantChatClient
                    .prompt()
                    .messages(springMessages)
                    .call()
                    .content();
        } finally {
            actions = menuAssistantActionContext.drainActions();
        }

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
