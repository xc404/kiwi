package com.kiwi.project.ai.bpm;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.system.ai.MenuAssistantTools;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BPM 设计器专用逻辑：登记前端可执行动作；对外 MCP/ChatClient 通过 {@link BpmDesignerActionsCtl} 暴露。
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

    public String registerBpmnXml(String xml) {
        try {
            xmlValidator.validate(xml);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        actionContext.add(BpmDesignerAction.bpmnXml(xml));
        return "已登记导入 BPMN XML（长度 " + xml.length() + "）。";
    }

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

    public String navigate(String routePath, String queryParamsJson) {
        return menuAssistantTools.navigate(routePath, queryParamsJson);
    }
}
