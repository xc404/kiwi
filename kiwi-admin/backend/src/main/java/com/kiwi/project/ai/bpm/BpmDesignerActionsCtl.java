package com.kiwi.project.ai.bpm;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将原 {@link BpmDesignerTools} 上的 MCP 能力以 REST + OpenAPI 暴露。
 */
@SaCheckLogin
@RestController
@RequestMapping("/ai/mcp/bpm-designer")
@RequiredArgsConstructor
@Tag(name = "BPM 设计器动作", description = "工具栏登记、BPMN XML、组件库等与 MCP 工具一致")
public class BpmDesignerActionsCtl {

    private final BpmDesignerTools bpmDesignerTools;

    @PostMapping("/toolbar")
    @Operation(
            operationId = "bpm_designer_register_toolbar",
            summary = "登记一个与 BPM 设计器工具栏等价的操作，由前端执行",
            description = "command 必须是：undo, redo, copy, paste, removeSelection, find, zoomIn, zoomOut, zoomFit, save, deploy, start, saveAsComponent, exportXml, exportSvg。")
    public String registerToolbar(@RequestParam String command) {
        return bpmDesignerTools.registerToolbar(command);
    }

    @PostMapping("/bpmn-xml")
    @Operation(
            operationId = "bpm_designer_set_bpmn_xml",
            summary = "登记用完整 BPMN 2.0 XML 替换当前画布",
            description = "definitions 根元素；服务端做基础校验，持久化需用户在前端保存。")
    public String registerBpmnXml(@RequestBody BpmnXmlBody body) {
        return bpmDesignerTools.registerBpmnXml(body.getXml());
    }

    @PostMapping("/append-component")
    @Operation(
            operationId = "bpm_designer_append_component",
            summary = "登记在图中追加一个业务组件",
            description = "componentId 为组件库中的组件 id，可先调用 bpm_designer_list_components。sourceElementId 可选：作为追加起点的图形元素 id（BPMN element id）。")
    public String registerAppendComponent(
            @RequestParam String componentId,
            @RequestParam(required = false) String sourceElementId) {
        return bpmDesignerTools.registerAppendComponent(componentId, sourceElementId);
    }

    @GetMapping("/components")
    @Operation(
            operationId = "bpm_designer_list_components",
            summary = "列出组件库中的 BPM 组件",
            description = "返回 id、name、key，供选择追加或向用户说明。")
    public String listComponents() {
        return bpmDesignerTools.listComponents();
    }

    @PostMapping("/navigate")
    @Operation(
            operationId = "bpm_designer_navigate",
            summary = "登记前端跳转到应用内页面",
            description = "routePath 须与当前用户可见菜单 path 一致；queryParamsJson 可选 JSON 对象字符串。")
    public String navigate(
            @RequestParam String routePath,
            @RequestParam(required = false) String queryParamsJson) {
        return bpmDesignerTools.navigate(routePath, queryParamsJson);
    }

    @Data
    public static class BpmnXmlBody {
        private String xml;
    }
}
