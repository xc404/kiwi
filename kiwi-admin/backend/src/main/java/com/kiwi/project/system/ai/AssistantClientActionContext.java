package com.kiwi.project.system.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AiAssistantResponse;
import com.kiwi.project.ai.bpm.BpmDesignerXmlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 单次 {@code /ai/assistant} 请求内，与工具回调同线程收集需前端执行的 {@link AiAssistantResponse.ClientAction}。
 * 菜单跳转与 BPM 设计器建议动作（工具栏、XML、追加组件）均登记到此，由模型按需通过 MCP 工具调用。
 */
@Component
@RequiredArgsConstructor
public class AssistantClientActionContext {

    private static final Set<String> DEFAULT_TOOLBAR_COMMANDS = Set.of(
            "undo", "redo", "copy", "paste", "removeSelection", "find",
            "zoomIn", "zoomOut", "zoomFit",
            "save", "deploy", "start", "saveAsComponent",
            "exportXml", "exportSvg"
    );

    private static final ThreadLocal<List<AiAssistantResponse.ClientAction>> ACTIONS =
            ThreadLocal.withInitial(ArrayList::new);

    private final MenuNavigatePathValidator menuNavigatePathValidator;
    private final BpmDesignerXmlValidator xmlValidator;
    private final ObjectMapper objectMapper;

    public void beginRequest() {
        ACTIONS.remove();
    }

    public Optional<String> addNavigate(String path, Map<String, String> queryParams) {
        Optional<String> err = menuNavigatePathValidator.validate(path);
        if (err.isPresent()) {
            return err;
        }
        Map<String, String> q = queryParams != null ? new LinkedHashMap<>(queryParams) : new LinkedHashMap<>();
        ACTIONS.get().add(AiAssistantResponse.navigate(path.trim(), q));
        return Optional.empty();
    }

    /**
     * 登记设计器工具栏命令（须为允许集合内之一）。
     */
    public Optional<String> addDesignerToolbar(String command, String toolbarOptionsJson) {
        if (command == null || command.isBlank()) {
            return Optional.of("command 不能为空。");
        }
        String cmd = command.trim();
        if (!DEFAULT_TOOLBAR_COMMANDS.contains(cmd)) {
            return Optional.of("不支持的 toolbar 命令: " + cmd);
        }
        Map<String, Object> options;
        try {
            options = parseToolbarOptionsJson(toolbarOptionsJson);
        } catch (IllegalArgumentException ex) {
            return Optional.of(ex.getMessage());
        }
        AiAssistantResponse.ClientAction a = new AiAssistantResponse.ClientAction();
        a.setType("toolbar");
        a.setToolbarCommand(cmd);
        a.setToolbarOptions(options);
        ACTIONS.get().add(a);
        return Optional.empty();
    }

    /**
     * 登记替换/导入 BPMN XML 建议（经 XML 校验）。
     */
    public Optional<String> addDesignerBpmnXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return Optional.of("xml 不能为空。");
        }
        try {
            xmlValidator.validate(xml);
        } catch (IllegalArgumentException ex) {
            return Optional.of(ex.getMessage());
        }
        AiAssistantResponse.ClientAction a = new AiAssistantResponse.ClientAction();
        a.setType("bpmnXml");
        a.setXml(xml);
        ACTIONS.get().add(a);
        return Optional.empty();
    }

    /**
     * 登记从组件库追加组件到画布的建议。
     */
    public Optional<String> addDesignerAppendComponent(String componentId, String sourceElementId) {
        if (componentId == null || componentId.isBlank()) {
            return Optional.of("componentId 不能为空。");
        }
        AiAssistantResponse.ClientAction a = new AiAssistantResponse.ClientAction();
        a.setType("appendComponent");
        a.setComponentId(componentId.trim());
        if (sourceElementId != null && !sourceElementId.isBlank()) {
            a.setSourceElementId(sourceElementId.trim());
        }
        ACTIONS.get().add(a);
        return Optional.empty();
    }

    public List<AiAssistantResponse.ClientAction> drainActions() {
        try {
            return List.copyOf(ACTIONS.get());
        } finally {
            ACTIONS.remove();
        }
    }

    private Map<String, Object> parseToolbarOptionsJson(String toolbarOptionsJson) {
        if (toolbarOptionsJson == null || toolbarOptionsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode root = objectMapper.readTree(toolbarOptionsJson.trim());
            if (!root.isObject()) {
                throw new IllegalArgumentException("toolbarOptionsJson 须为 JSON 对象。");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            root.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (v != null && v.isValueNode()) {
                    if (v.isNumber()) {
                        out.put(e.getKey(), v.numberValue());
                    } else if (v.isBoolean()) {
                        out.put(e.getKey(), v.booleanValue());
                    } else {
                        out.put(e.getKey(), v.asText());
                    }
                }
            });
            return out;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("toolbarOptionsJson 解析失败。");
        }
    }
}
