package com.kiwi.project.system.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AssistantClientActionContext;
import com.kiwi.project.ai.ClientAction;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 助手侧「BPM 设计器」前端动作登记：{@code assistant_designer_*}，写入 {@link AssistantClientActionContext}。
 */
@Service
@RequiredArgsConstructor
public class AssistantDesignerTools {

    private static final Set<String> DEFAULT_TOOLBAR_COMMANDS = Set.of(
            "undo", "redo", "copy", "paste", "removeSelection", "find",
            "zoomIn", "zoomOut", "zoomFit",
            "save", "deploy", "start", "saveAsComponent",
            "exportXml", "exportSvg"
    );

    private final AssistantClientActionContext assistantClientActionContext;
    private final BpmDesignerXmlValidator xmlValidator;
    private final ObjectMapper objectMapper;

    @Tool(
            name = "assistant_designer_toolbar",
            description = "当用户讨论 BPMN/流程图编辑且需驱动画布工具栏时调用。command 为设计器约定命令字；"
                    + "toolbarOptionsJson 可选，为 JSON 对象字符串。")
    public String designerToolbar(String command, String toolbarOptionsJson) {
        if (command == null || command.isBlank()) {
            return "command 不能为空。";
        }
        String cmd = command.trim();
        if (!DEFAULT_TOOLBAR_COMMANDS.contains(cmd)) {
            return "不支持的 toolbar 命令: " + cmd;
        }
        Map<String, Object> options;
        try {
            options = parseToolbarOptionsJson(toolbarOptionsJson);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        assistantClientActionContext.addClientAction(ClientAction.toolbar(cmd, options));
        return "已登记设计器工具栏动作：" + cmd + "。";
    }

    @Tool(
            name = "assistant_designer_bpmn_xml",
            description = "登记 BPMN XML 替换建议；服务端校验可解析且根元素为 definitions。")
    public String designerBpmnXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return "xml 不能为空。";
        }
        try {
            xmlValidator.validate(xml);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        assistantClientActionContext.addClientAction(ClientAction.bpmnXml(xml));
        return "已登记 BPMN XML 更新建议（长度=" + xml.length() + "）。";
    }

    @Tool(
            name = "assistant_designer_append_component",
            description = "登记从组件库追加组件到画布。componentId 与组件库 id 一致；sourceElementId 可选，表示相对哪个画布元素追加。")
    public String designerAppendComponent(String componentId, String sourceElementId) {
        if (componentId == null || componentId.isBlank()) {
            return "componentId 不能为空。";
        }
        assistantClientActionContext.addClientAction(ClientAction.appendComponent(componentId, sourceElementId));
        return "已登记追加组件建议：componentId=" + componentId.trim()
                + (sourceElementId != null && !sourceElementId.isBlank() ? "，sourceElementId=" + sourceElementId.trim() : "")
                + "。";
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
