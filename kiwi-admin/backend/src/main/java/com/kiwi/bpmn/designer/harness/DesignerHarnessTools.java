package com.kiwi.bpmn.designer.harness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.harness.bpmn.DesignerHarnessBpmnApplyService;
import com.kiwi.bpmn.designer.harness.model.EditOperation;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DesignerHarnessTools {

    private final BpmComponentService bpmComponentService;
    private final DesignerHarnessBpmnApplyService applyService;
    private final DesignerHarnessTurnContext turnContext;
    private final ObjectMapper objectMapper;

    @Tool(
            name = "search_components",
            description = "按关键词搜索 BPM 组件库。返回少量 id/name/group/description，不要一次拉全库。"
                    + "用户要加业务节点时先搜，再 get_component 看参数，最后 apply_bpmn_ops。")
    public String searchComponents(@ToolParam(description = "关键词，如 http、文件、Mongo") String query) {
        String q = StringUtils.trimToEmpty(query).toLowerCase(Locale.ROOT);
        List<BpmComponent> all = bpmComponentService.listCachedComponents();
        List<String> lines = new ArrayList<>();
        for (BpmComponent raw : all) {
            if (raw == null || StringUtils.isBlank(raw.getId())) {
                continue;
            }
            BpmComponent c = bpmComponentService.fillComponentProperties(raw);
            if (!q.isEmpty() && !matches(c, q)) {
                continue;
            }
            lines.add(c.getId() + " | " + StringUtils.defaultString(c.getName())
                    + " | " + StringUtils.defaultString(c.getGroup())
                    + " | " + StringUtils.abbreviate(StringUtils.defaultString(c.getDescription()), 80));
            if (lines.size() >= 15) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return "没有匹配的组件。换个关键词再搜。";
        }
        return String.join("\n", lines);
    }

    @Tool(
            name = "get_component",
            description = "按 componentId 取组件参数契约（key、是否必填、说明）。addNode 的 serviceTask 必须先调用本工具再填 parameters。")
    public String getComponent(@ToolParam(description = "组件 id，例如 classpath_httpRequest") String componentId) {
        if (StringUtils.isBlank(componentId)) {
            return "componentId 不能为空";
        }
        BpmComponent c = bpmComponentService.resolveComponentById(componentId.trim());
        if (c == null) {
            return "找不到组件: " + componentId;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(c.getId())
                .append(" name=").append(StringUtils.defaultString(c.getName()))
                .append(" group=").append(StringUtils.defaultString(c.getGroup()))
                .append("\n").append(StringUtils.defaultString(c.getDescription()))
                .append("\ninputs:\n");
        List<BpmComponentParameter> inputs = c.getInputParameters();
        if (inputs == null || inputs.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            for (BpmComponentParameter p : inputs) {
                if (p == null || p.isHidden()) {
                    continue;
                }
                sb.append("  - ").append(p.getKey())
                        .append(" required=").append(p.isRequired())
                        .append(" type=").append(StringUtils.defaultString(p.getType()))
                        .append(" ").append(StringUtils.defaultString(p.getDescription()))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    @Tool(
            name = "apply_bpmn_ops",
            description = "唯一改图入口。参数 operationsJson 为 JSON 数组，元素含 op："
                    + "addNode(node, afterRef?, beforeRef?)、removeNode(nodeId)、updateNode(nodeId, patch)、"
                    + "addFlow(flow)、removeFlow(flowId)、setProcessMeta(name)。"
                    + "node.type 为 startEvent|endEvent|serviceTask|userTask|exclusiveGateway；"
                    + "serviceTask 必须带已存在的 componentId。"
                    + "成功则立刻保存流程定义并刷新画布；禁止输出 BPMN XML。")
    public String applyBpmnOps(
            @ToolParam(description = "JSON 数组，元素含 op: addNode/removeNode/updateNode/addFlow/removeFlow/setProcessMeta")
            String operationsJson) {
        DesignerHarnessTurnContext.Slot slot = turnContext.require();
        List<EditOperation> operations;
        try {
            operations = parseOperations(operationsJson);
        } catch (Exception e) {
            return "operationsJson 无法解析: " + e.getMessage();
        }
        if (operations.isEmpty()) {
            return "operations 为空，未改图。";
        }
        DesignerHarnessBpmnApplyService.ApplyResult result =
                applyService.applyAndSave(slot.session.getId(), slot.bpmnXml, operations);
        if (result.ok()) {
            slot.bpmnXml = result.xml();
        }
        return result.toToolText();
    }

    private List<EditOperation> parseOperations(String operationsJson) throws Exception {
        JsonNode node = objectMapper.readTree(StringUtils.defaultIfBlank(operationsJson, "[]"));
        if (node.isObject()) {
            return List.of(objectMapper.treeToValue(node, EditOperation.class));
        }
        return objectMapper.convertValue(node, new TypeReference<>() {});
    }

    private boolean matches(BpmComponent c, String q) {
        return contains(c.getId(), q)
                || contains(c.getName(), q)
                || contains(c.getKey(), q)
                || contains(c.getGroup(), q)
                || contains(c.getDescription(), q);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }
}