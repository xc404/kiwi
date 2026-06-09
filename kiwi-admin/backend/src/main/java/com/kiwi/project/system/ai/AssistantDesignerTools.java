package com.kiwi.project.system.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AssistantClientActionContext;
import com.kiwi.project.ai.ClientAction;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.service.BpmComponentService;
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
    private final BpmComponentService bpmComponentService;
    private final ObjectMapper objectMapper;

//    @Tool(
//            name = "assistant_designer_toolbar",
//            description = "仅用于设计器已实现的 toolbar 能力（undo/redo/zoom/copy/paste/removeSelection/find/save/deploy/start/export 等）。"
//                    + "改节点参数、复制配置、增删连线/节点等须用 assistant_designer_bpmn_xml，不要用 toolbar 代替。"
//                    + "toolbarOptionsJson 可选，为 JSON 对象字符串。")
//    public String designerToolbar(String command, String toolbarOptionsJson) {
//        if (command == null || command.isBlank()) {
//            return "command 不能为空。";
//        }
//        String cmd = command.trim();
//        if (!DEFAULT_TOOLBAR_COMMANDS.contains(cmd)) {
//            return "不支持的 toolbar 命令: " + cmd;
//        }
//        Map<String, Object> options;
//        try {
//            options = parseToolbarOptionsJson(toolbarOptionsJson);
//        } catch (IllegalArgumentException ex) {
//            return ex.getMessage();
//        }
//        assistantClientActionContext.addClientAction(ClientAction.toolbar(cmd, options));
//        return "已登记设计器工具栏动作：" + cmd + "。";
//    }

    @Tool(
            name = "assistant_designer_bpmn_xml",
            description = "【设计器改 BPMN 必调】登记完整 BPMN 2.0 definitions XML：凡 toolbar 无法完成的编辑（改参数、复制它流程配置、删除/移除节点、增删连线等）均须调用；"
                    + "前端将自动 import 并保存到当前流程。服务端校验可解析且根为 definitions。")
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
            name = "assistant_designer_match_component",
            description = "【加组件必调】用户要在流程图追加业务组件时调用。"
                    + "componentId 必须从对话上下文中「组件库 componentId|name」列表选取精确 id（由大模型根据用户描述匹配）；"
                    + "不要传画布元素 id，锚点由前端决定。")
    public String designerMatchComponent(String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return "componentId 不能为空。";
        }
        BpmComponent component = bpmComponentService.resolveComponentById(componentId.trim());
        if (component == null) {
            return "组件库中不存在 componentId=" + componentId.trim()
                    + "，请根据上下文组件列表重新选择精确 id。";
        }
        return registerMatchComponent(component);
    }

    private String registerMatchComponent(BpmComponent component) {
        assistantClientActionContext.addClientAction(
                ClientAction.matchComponent(component.getId(), component.getName()));
        return "已匹配组件：componentId=" + component.getId()
                + (component.getName() != null ? "，name=" + component.getName() : "")
                + "。画布追加由前端执行；若需指定锚点请让用户选中节点或说明元素 id。";
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
