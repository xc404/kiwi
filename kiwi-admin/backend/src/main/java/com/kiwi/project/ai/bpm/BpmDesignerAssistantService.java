package com.kiwi.project.ai.bpm;

import com.kiwi.project.ai.AiChatMessage;
import com.kiwi.project.ai.AiChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BpmDesignerAssistantService {

    private static final int MAX_CONTEXT_XML_CHARS = 24_000;
    private static final Set<String> DEFAULT_TOOLBAR_COMMANDS = Set.of(
            "undo", "redo", "copy", "paste", "removeSelection", "find",
            "zoomIn", "zoomOut", "zoomFit",
            "save", "deploy", "start", "saveAsComponent",
            "exportXml", "exportSvg"
    );

    private static final String SYSTEM_BASE = """
            你是 Kiwi BPM 流程设计器中的 AI 助手。用户正在编辑 BPMN 流程图。
            你不能执行任何真实操作，只能建议动作。
            你的输出必须是一个 JSON 对象，格式如下：
            {
              "content": "给用户看的中文回复",
              "actions": [
                { "type": "toolbar", "toolbarCommand": "undo", "toolbarOptions": {} },
                { "type": "bpmnXml", "xml": "<bpmn.../>" },
                { "type": "appendComponent", "componentId": "xxx", "sourceElementId": "Task_1" },
                { "type": "navigate", "path": "/system/user", "queryParams": {"k":"v"} }
              ]
            }
            - 若不需要动作，actions 返回空数组。
            - 不要输出 markdown 代码块，不要输出 JSON 之外的任何文字。
            - 若用户意图不明确，可只在 content 中追问，actions 为空。
            """;

    private final ChatClient bpmDesignerChatClient;
    private final AiChatProperties properties;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmDesignerXmlValidator xmlValidator;
    private final ObjectMapper objectMapper;

    public BpmDesignerAssistantService(
            @Qualifier("bpmDesignerChatClient") ChatClient bpmDesignerChatClient,
            AiChatProperties properties,
            BpmProcessDefinitionDao bpmProcessDefinitionDao,
            BpmDesignerXmlValidator xmlValidator,
            ObjectMapper objectMapper) {
        this.bpmDesignerChatClient = bpmDesignerChatClient;
        this.properties = properties;
        this.bpmProcessDefinitionDao = bpmProcessDefinitionDao;
        this.xmlValidator = xmlValidator;
        this.objectMapper = objectMapper;
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
                + "。\n下列为当前 BPMN XML 片段：\n"
                + xmlSnippet;
        String capabilityBlock = buildCapabilityBlock(request.getClientCapabilities());

        List<Message> springMessages = new ArrayList<>();
        springMessages.add(new SystemMessage(SYSTEM_BASE + "\n\n" + capabilityBlock + "\n\n" + contextBlock));
        for (AiChatMessage m : request.getMessages()) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            springMessages.add(toSpringMessage(m));
        }
        if (springMessages.size() < 2) {
            throw new IllegalArgumentException("没有有效的对话内容");
        }

        String raw = bpmDesignerChatClient
                .prompt()
                .messages(springMessages)
                .call()
                .content();
        ParsedModelReply parsed = parseModelReply(raw);
        List<BpmDesignerAction> actions = sanitizeActions(parsed.actions, request.getClientCapabilities());

        BpmDesignerAssistantResponse out = new BpmDesignerAssistantResponse();
        out.setContent(parsed.content);
        out.setActions(actions);
        return out;
    }

    private List<BpmDesignerAction> sanitizeActions(
            List<BpmDesignerAction> actions,
            BpmDesignerAssistantRequest.ClientCapabilities capabilities) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        Set<String> toolbarCommands = resolveToolbarCommands(capabilities);
        boolean allowBpmnXml = capabilities == null || capabilities.getAllowBpmnXml() == null || capabilities.getAllowBpmnXml();
        boolean allowAppendComponent = capabilities == null || capabilities.getAllowAppendComponent() == null || capabilities.getAllowAppendComponent();
        boolean allowNavigate = capabilities == null || capabilities.getAllowNavigate() == null || capabilities.getAllowNavigate();

        List<BpmDesignerAction> out = new ArrayList<>();
        for (BpmDesignerAction action : actions) {
            if (action == null || action.getType() == null) {
                continue;
            }
            String type = action.getType().trim();
            switch (type) {
                case "toolbar" -> {
                    String cmd = action.getToolbarCommand();
                    if (cmd == null || !toolbarCommands.contains(cmd.trim())) {
                        continue;
                    }
                    BpmDesignerAction safe = BpmDesignerAction.toolbar(cmd.trim(), toSafeObjectMap(action.getToolbarOptions()));
                    out.add(safe);
                }
                case "bpmnXml" -> {
                    if (!allowBpmnXml || action.getXml() == null || action.getXml().isBlank()) {
                        continue;
                    }
                    try {
                        xmlValidator.validate(action.getXml());
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    out.add(BpmDesignerAction.bpmnXml(action.getXml()));
                }
                case "appendComponent" -> {
                    if (!allowAppendComponent || action.getComponentId() == null || action.getComponentId().isBlank()) {
                        continue;
                    }
                    String sourceId = action.getSourceElementId();
                    out.add(BpmDesignerAction.appendComponent(
                            action.getComponentId().trim(),
                            sourceId != null && !sourceId.isBlank() ? sourceId.trim() : null));
                }
                case "navigate" -> {
                    if (!allowNavigate || action.getPath() == null || action.getPath().isBlank()) {
                        continue;
                    }
                    out.add(BpmDesignerAction.navigate(action.getPath().trim(), toSafeStringMap(action.getQueryParams())));
                }
                default -> {
                    // Ignore unsupported action types.
                }
            }
        }
        return out;
    }

    private Set<String> resolveToolbarCommands(BpmDesignerAssistantRequest.ClientCapabilities capabilities) {
        if (capabilities == null || capabilities.getToolbarCommands() == null || capabilities.getToolbarCommands().isEmpty()) {
            return DEFAULT_TOOLBAR_COMMANDS;
        }
        return capabilities.getToolbarCommands().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    private String buildCapabilityBlock(BpmDesignerAssistantRequest.ClientCapabilities capabilities) {
        Set<String> toolbar = resolveToolbarCommands(capabilities);
        boolean allowBpmnXml = capabilities == null || capabilities.getAllowBpmnXml() == null || capabilities.getAllowBpmnXml();
        boolean allowAppendComponent = capabilities == null || capabilities.getAllowAppendComponent() == null || capabilities.getAllowAppendComponent();
        boolean allowNavigate = capabilities == null || capabilities.getAllowNavigate() == null || capabilities.getAllowNavigate();
        String componentHints = "";
        if (capabilities != null && capabilities.getAvailableComponents() != null && !capabilities.getAvailableComponents().isEmpty()) {
            componentHints = "\n可选组件（componentId -> name）:\n" + capabilities.getAvailableComponents().entrySet().stream()
                    .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                    .map(e -> "- " + e.getKey().trim() + " => " + (e.getValue() != null ? e.getValue().trim() : ""))
                    .collect(Collectors.joining("\n"));
        }
        return "前端能力约束：\n"
                + "- toolbarCommands: " + toolbar + "\n"
                + "- allowBpmnXml: " + allowBpmnXml + "\n"
                + "- allowAppendComponent: " + allowAppendComponent + "\n"
                + "- allowNavigate: " + allowNavigate
                + componentHints;
    }

    private ParsedModelReply parseModelReply(String raw) {
        String fallback = raw == null || raw.isBlank() ? "（模型未返回文本，请重试。）" : raw.trim();
        String json = extractJson(raw);
        if (json == null) {
            return new ParsedModelReply(fallback, List.of());
        }
        try {
            ModelReply parsed = objectMapper.readValue(json, ModelReply.class);
            String content = parsed.content == null || parsed.content.isBlank() ? "（模型未返回文本，请重试。）" : parsed.content.trim();
            return new ParsedModelReply(content, parsed.actions != null ? parsed.actions : List.of());
        } catch (Exception ex) {
            return new ParsedModelReply(fallback, List.of());
        }
    }

    private static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstBrace = t.indexOf('{');
            int lastBrace = t.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return t.substring(firstBrace, lastBrace + 1);
            }
            return null;
        }
        if (t.startsWith("{") && t.endsWith("}")) {
            return t;
        }
        int firstBrace = t.indexOf('{');
        int lastBrace = t.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return t.substring(firstBrace, lastBrace + 1);
        }
        return null;
    }

    private static Map<String, Object> toSafeObjectMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(map);
    }

    private static Map<String, String> toSafeStringMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                out.put(k.trim(), v == null ? "" : v.trim());
            }
        });
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

    private static class ModelReply {
        public String content;
        public List<BpmDesignerAction> actions;
    }

    private record ParsedModelReply(String content, List<BpmDesignerAction> actions) {
    }
}
