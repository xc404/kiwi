package com.kiwi.project.ai.bpm;

import com.kiwi.project.ai.AiAssistantResponse;
import com.kiwi.project.ai.AiChatMessage;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.system.ai.MenuAssistantActionContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class BpmDesignerAssistantService {

    private static final int MAX_CONTEXT_XML_CHARS = 24_000;

    private static final String SYSTEM_BASE = """
            你是 Kiwi BPM 流程设计器中的 AI 助手。用户正在编辑 BPMN 流程图。
            你可以使用工具登记操作（撤销、缩放、保存、部署、替换 BPMN XML、追加组件库组件、跳转页面等）。
            不要编造已执行的操作；需要改图或执行按钮时务必调用对应工具登记。
            若用户意图不明确，先简短追问。回答使用中文。
            """;

    private final ChatClient bpmDesignerChatClient;
    private final AiChatProperties properties;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmDesignerActionContext bpmDesignerActionContext;
    private final MenuAssistantActionContext menuAssistantActionContext;

    public BpmDesignerAssistantService(
            @Qualifier("bpmDesignerChatClient") ChatClient bpmDesignerChatClient,
            AiChatProperties properties,
            BpmProcessDefinitionDao bpmProcessDefinitionDao,
            BpmDesignerActionContext bpmDesignerActionContext,
            MenuAssistantActionContext menuAssistantActionContext) {
        this.bpmDesignerChatClient = bpmDesignerChatClient;
        this.properties = properties;
        this.bpmProcessDefinitionDao = bpmProcessDefinitionDao;
        this.bpmDesignerActionContext = bpmDesignerActionContext;
        this.menuAssistantActionContext = menuAssistantActionContext;
    }

    public BpmDesignerAssistantResponse run(BpmDesignerAssistantRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI 对话未启用（kiwi.ai.enabled=false）");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        if (request.getProcessId() == null || request.getProcessId().isBlank()) {
            throw new IllegalArgumentException("processId 不能为空");
        }

        BpmProcess process = bpmProcessDefinitionDao.findById(request.getProcessId().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程不存在"));

        String effectiveXml = request.getBpmnXml();
        if (effectiveXml == null || effectiveXml.isBlank()) {
            effectiveXml = process.getBpmnXml();
        }
        String xmlSnippet = truncate(effectiveXml, MAX_CONTEXT_XML_CHARS);

        String contextBlock = "当前流程 id=" + process.getId()
                + "，名称=" + (process.getName() != null ? process.getName() : "")
                + "。\n下列为当前 BPMN XML 片段（理解用；修改图请用工具登记）：\n"
                + xmlSnippet;

        bpmDesignerActionContext.beginRequest();
        menuAssistantActionContext.beginRequest();

        List<Message> springMessages = new ArrayList<>();
        springMessages.add(new SystemMessage(SYSTEM_BASE + "\n\n" + contextBlock));
        for (AiChatMessage m : request.getMessages()) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            springMessages.add(toSpringMessage(m));
        }
        if (springMessages.size() < 2) {
            throw new IllegalArgumentException("没有有效的对话内容");
        }

        String content = bpmDesignerChatClient
                .prompt()
                .messages(springMessages)
                .call()
                .content();

        if (content == null || content.isBlank()) {
            content = "（模型未返回文本，请重试。）";
        }

        List<BpmDesignerAction> actions = new ArrayList<>(bpmDesignerActionContext.drainActions());
        for (AiAssistantResponse.ClientAction nav : menuAssistantActionContext.drainActions()) {
            if ("navigate".equals(nav.getType()) && nav.getPath() != null) {
                actions.add(BpmDesignerAction.navigate(nav.getPath(), nav.getQueryParams()));
            }
        }

        BpmDesignerAssistantResponse out = new BpmDesignerAssistantResponse();
        out.setContent(content.trim());
        out.setActions(actions);
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n…（已截断）";
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
