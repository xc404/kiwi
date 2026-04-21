package com.kiwi.project.ai.bpm;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.system.ai.MenuAssistantTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BPM 设计器专用 Spring AI 工具：登记前端可执行动作，不直接改库。
 */
@Service
@RequiredArgsConstructor
public class BpmDesignerTools {

    private static final Set<String> TOOLBAR_COMMANDS = Set.of(
            "undo", "redo", "copy", "paste", "removeSelection", "find",
            "zoomIn", "zoomOut", "zoomFit",
            "save", "deploy", "start", "saveAsComponent",
            "exportXml", "exportSvg"
    );

    private final BpmDesignerActionContext actionContext;
    private final BpmDesignerXmlValidator xmlValidator;
    private final BpmComponentDao bpmComponentDao;
    private final MenuAssistantTools menuAssistantTools;

    @Tool(
            name = "bpm_designer_register_toolbar",
            description = "登记一个与 BPM 设计器工具栏等价的操作，由前端执行。"
                    + " command 必须是：undo, redo, copy, paste, removeSelection, find, "
                    + "zoomIn, zoomOut, zoomFit, save, deploy, start, saveAsComponent, exportXml, exportSvg。")
    public String registerToolbar(String command) {
        if (command == null || command.isBlank()) {
            return "command 不能为空。";
        }
        String c = command.trim();
        if (!TOOLBAR_COMMANDS.contains(c)) {
            return "不支持的 command：" + c + "。允许值：" + TOOLBAR_COMMANDS;
        }
        Map<String, Object> opts = new LinkedHashMap<>();
        if ("zoomIn".equals(c)) {
            opts.put("value", 1);
            actionContext.add(BpmDesignerAction.toolbar("stepZoom", opts));
            return "已登记：放大画布。";
        }
        if ("zoomOut".equals(c)) {
            opts.put("value", -1);
            actionContext.add(BpmDesignerAction.toolbar("stepZoom", opts));
            return "已登记：缩小画布。";
        }
        if ("zoomFit".equals(c)) {
            opts.put("value", "fit-viewport");
            actionContext.add(BpmDesignerAction.toolbar("zoom", opts));
            return "已登记：适应画布。";
        }
        actionContext.add(BpmDesignerAction.toolbar(c, null));
        return "已登记工具栏操作：" + c + "。";
    }

    @Tool(
            name = "bpm_designer_set_bpmn_xml",
            description = "登记用完整 BPMN 2.0 XML（definitions 根元素）替换当前画布；服务端做基础校验，持久化需用户在前端保存。")
    public String registerBpmnXml(String xml) {
        try {
            xmlValidator.validate(xml);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        actionContext.add(BpmDesignerAction.bpmnXml(xml));
        return "已登记导入 BPMN XML（长度 " + xml.length() + "）。";
    }

    @Tool(
            name = "bpm_designer_append_component",
            description = "登记在图中追加一个业务组件。componentId 为组件库中的组件 id，可先调用 bpm_designer_list_components。"
                    + " sourceElementId 可选：作为追加起点的图形元素 id（BPMN element id）。")
    public String registerAppendComponent(String componentId, String sourceElementId) {
        if (componentId == null || componentId.isBlank()) {
            return "componentId 不能为空。";
        }
        String id = componentId.trim();
        if (bpmComponentDao.findById(id).isEmpty()) {
            return "未找到组件 id：" + id + "。请先调用 bpm_designer_list_components。";
        }
        actionContext.add(BpmDesignerAction.appendComponent(id,
                sourceElementId != null && !sourceElementId.isBlank() ? sourceElementId.trim() : null));
        return "已登记追加组件：" + id + "。";
    }

    @Tool(
            name = "bpm_designer_list_components",
            description = "列出组件库中的 BPM 组件（id、name、key），供选择追加或向用户说明。")
    public String listComponents() {
        var page = bpmComponentDao.findAll(PageRequest.of(0, 200));
        if (page.isEmpty()) {
            return "（组件库为空）";
        }
        return page.getContent().stream()
                .map(this::formatComponentLine)
                .collect(Collectors.joining("\n"));
    }

    private String formatComponentLine(BpmComponent c) {
        String key = c.getKey() != null ? c.getKey() : "";
        String name = c.getName() != null ? c.getName() : "";
        return "- id=" + c.getId() + ", key=" + key + ", name=" + name;
    }

    @Tool(
            name = "bpm_designer_navigate",
            description = "登记前端跳转到应用内页面。routePath 须与当前用户可见菜单 path 一致；queryParamsJson 可选 JSON 对象字符串。")
    public String navigate(String routePath, String queryParamsJson) {
        return menuAssistantTools.navigate(routePath, queryParamsJson);
    }
}
