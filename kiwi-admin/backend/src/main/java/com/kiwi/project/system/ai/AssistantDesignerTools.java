package com.kiwi.project.system.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 助手侧「BPM 设计器」前端动作登记：{@code assistant_designer_*}，写入 {@link AssistantClientActionContext}。
 */
@Service
@RequiredArgsConstructor
public class AssistantDesignerTools {

    private final AssistantClientActionContext assistantClientActionContext;

    @Tool(
            name = "assistant_designer_toolbar",
            description = "当用户讨论 BPMN/流程图编辑且需驱动画布工具栏时调用。command 为设计器约定命令字；"
                    + "toolbarOptionsJson 可选，为 JSON 对象字符串。")
    public String designerToolbar(String command, String toolbarOptionsJson) {
        Optional<String> err = assistantClientActionContext.addDesignerToolbar(command, toolbarOptionsJson);
        if (err.isPresent()) {
            return err.get();
        }
        return "已登记设计器工具栏动作：" + command.trim() + "。";
    }

    @Tool(
            name = "assistant_designer_bpmn_xml",
            description = "登记 BPMN XML 替换建议；服务端校验可解析且根元素为 definitions。")
    public String designerBpmnXml(String xml) {
        Optional<String> err = assistantClientActionContext.addDesignerBpmnXml(xml);
        if (err.isPresent()) {
            return err.get();
        }
        return "已登记 BPMN XML 更新建议（长度=" + xml.length() + "）。";
    }

    @Tool(
            name = "assistant_designer_append_component",
            description = "登记从组件库追加组件到画布。componentId 与组件库 id 一致；sourceElementId 可选，表示相对哪个画布元素追加。")
    public String designerAppendComponent(String componentId, String sourceElementId) {
        Optional<String> err = assistantClientActionContext.addDesignerAppendComponent(componentId, sourceElementId);
        if (err.isPresent()) {
            return err.get();
        }
        return "已登记追加组件建议：componentId=" + componentId.trim()
                + (sourceElementId != null && !sourceElementId.isBlank() ? "，sourceElementId=" + sourceElementId.trim() : "")
                + "。";
    }
}
