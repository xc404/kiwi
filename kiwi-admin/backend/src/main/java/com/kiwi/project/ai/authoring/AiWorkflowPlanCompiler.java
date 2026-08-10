package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将受限 Plan IR 确定性编译为 Kiwi 可导入的 BPMN XML。
 * LLM 负责业务计划，编译器负责命名空间、组件绑定、连线与基础 BPMNDI。
 */
@Component
public class AiWorkflowPlanCompiler {

    private final Pattern xmlIdPattern = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private final Set<String> supportedTypes = Set.of(
            "startEvent", "endEvent", "serviceTask", "userTask", "exclusiveGateway");

    private final ObjectMapper objectMapper;

    public AiWorkflowPlanCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<String> compile(String planIrJson, String catalogJson) {
        if (StringUtils.isBlank(planIrJson)) {
            return Optional.empty();
        }
        try {
            AiWorkflowPlan plan = objectMapper.readValue(planIrJson, AiWorkflowPlan.class);
            AiAuthoringCatalog catalog = objectMapper.readValue(
                    StringUtils.defaultIfBlank(catalogJson, "{}"), AiAuthoringCatalog.class);
            return Optional.of(compile(plan, catalog));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public String compile(AiWorkflowPlan plan, AiAuthoringCatalog catalog) {
        validatePlan(plan);
        Map<String, AiAuthoringCatalog.CatalogComponent> installed = installedIndex(catalog);
        Map<String, Box> boxes = layout(plan);
        String processId = StringUtils.defaultIfBlank(plan.getProcessId(), "ai_generated_process");

        StringBuilder nodes = new StringBuilder();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            appendNode(nodes, node, installed);
        }
        StringBuilder flows = new StringBuilder();
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            appendFlow(flows, flow);
        }
        StringBuilder shapes = new StringBuilder();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            appendShape(shapes, node, boxes.get(node.getId()));
        }
        StringBuilder edges = new StringBuilder();
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            appendEdge(edges, flow, boxes);
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="http://kiwi.io/schema/bpmn"
                                  id="Definitions_%s"
                                  targetNamespace="http://kiwi.io/ai-authoring">
                  <bpmn:process id="%s" name="%s" isExecutable="true">
                %s%s  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_%s">
                    <bpmndi:BPMNPlane id="BPMNPlane_%s" bpmnElement="%s">
                %s%s    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """.formatted(
                escape(processId),
                escape(processId),
                escape(StringUtils.defaultIfBlank(plan.getName(), "AI Generated Workflow")),
                nodes,
                flows,
                escape(processId),
                escape(processId),
                escape(processId),
                shapes,
                edges);
    }

    private void validatePlan(AiWorkflowPlan plan) {
        if (plan == null || plan.getNodes() == null || plan.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Plan IR 缺少 nodes");
        }
        if (StringUtils.isNotBlank(plan.getProcessId()) && !validId(plan.getProcessId())) {
            throw new IllegalArgumentException("processId 非法");
        }
        Set<String> ids = new LinkedHashSet<>();
        int starts = 0;
        int ends = 0;
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            if (node == null || !validId(node.getId()) || !supportedTypes.contains(node.getType())) {
                throw new IllegalArgumentException("Plan IR 节点非法");
            }
            if (!ids.add(node.getId())) {
                throw new IllegalArgumentException("Plan IR 节点 id 重复: " + node.getId());
            }
            if ("startEvent".equals(node.getType())) {
                starts++;
            } else if ("endEvent".equals(node.getType())) {
                ends++;
            }
        }
        if (starts != 1 || ends < 1) {
            throw new IllegalArgumentException("Plan IR 必须有一个 startEvent 和至少一个 endEvent");
        }
        if (plan.getFlows() == null || plan.getFlows().isEmpty()) {
            throw new IllegalArgumentException("Plan IR 缺少 flows");
        }
        Set<String> flowIds = new LinkedHashSet<>();
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            if (flow == null || !validId(flow.getId()) || !flowIds.add(flow.getId())
                    || !ids.contains(flow.getSourceRef()) || !ids.contains(flow.getTargetRef())) {
                throw new IllegalArgumentException("Plan IR 连线非法");
            }
        }
    }

    private Map<String, AiAuthoringCatalog.CatalogComponent> installedIndex(AiAuthoringCatalog catalog) {
        Map<String, AiAuthoringCatalog.CatalogComponent> result = new LinkedHashMap<>();
        if (catalog == null || catalog.getInstalled() == null) {
            return result;
        }
        for (AiAuthoringCatalog.CatalogComponent component : catalog.getInstalled()) {
            if (component != null && StringUtils.isNotBlank(component.getId())) {
                result.put(component.getId(), component);
            }
        }
        return result;
    }

    private void appendNode(
            StringBuilder xml,
            AiWorkflowPlan.Node node,
            Map<String, AiAuthoringCatalog.CatalogComponent> installed) {
        String id = escape(node.getId());
        String name = StringUtils.isBlank(node.getName()) ? "" : " name=\"" + escape(node.getName()) + "\"";
        switch (node.getType()) {
            case "startEvent" -> xml.append("    <bpmn:startEvent id=\"").append(id).append("\"")
                    .append(name).append("/>\n");
            case "endEvent" -> xml.append("    <bpmn:endEvent id=\"").append(id).append("\"")
                    .append(name).append("/>\n");
            case "exclusiveGateway" -> xml.append("    <bpmn:exclusiveGateway id=\"").append(id).append("\"")
                    .append(name).append("/>\n");
            case "userTask" -> xml.append("    <bpmn:userTask id=\"").append(id).append("\"")
                    .append(name).append("/>\n");
            case "serviceTask" -> appendServiceTask(xml, node, installed, id, name);
            default -> throw new IllegalArgumentException("不支持的节点类型: " + node.getType());
        }
    }

    private void appendServiceTask(
            StringBuilder xml,
            AiWorkflowPlan.Node node,
            Map<String, AiAuthoringCatalog.CatalogComponent> installed,
            String id,
            String nameAttribute) {
        AiAuthoringCatalog.CatalogComponent component = installed.get(node.getComponentId());
        if (component == null) {
            throw new IllegalArgumentException("componentId 不在 Catalog.installed: " + node.getComponentId());
        }
        String delegate = StringUtils.defaultIfBlank(
                component.getDelegateExpression(), "${" + beanName(component.getId()) + "}");
        xml.append("    <bpmn:serviceTask id=\"").append(id).append("\"")
                .append(nameAttribute)
                .append(" camunda:delegateExpression=\"").append(escape(delegate)).append("\"")
                .append(" kiwi:componentId=\"").append(escape(component.getId())).append("\"");
        if (node.getParameters() == null || node.getParameters().isEmpty()) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n")
                .append("      <bpmn:extensionElements>\n")
                .append("        <camunda:inputOutput>\n");
        for (Map.Entry<String, Object> parameter : node.getParameters().entrySet()) {
            if (StringUtils.isBlank(parameter.getKey())) {
                continue;
            }
            xml.append("          <camunda:inputParameter name=\"")
                    .append(escape(parameter.getKey())).append("\">")
                    .append(escape(parameterValue(parameter.getValue())))
                    .append("</camunda:inputParameter>\n");
        }
        xml.append("        </camunda:inputOutput>\n")
                .append("      </bpmn:extensionElements>\n")
                .append("    </bpmn:serviceTask>\n");
    }

    private void appendFlow(StringBuilder xml, AiWorkflowPlan.Flow flow) {
        xml.append("    <bpmn:sequenceFlow id=\"").append(escape(flow.getId()))
                .append("\" sourceRef=\"").append(escape(flow.getSourceRef()))
                .append("\" targetRef=\"").append(escape(flow.getTargetRef())).append("\"");
        if (StringUtils.isBlank(flow.getCondition())) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n")
                .append("      <bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">")
                .append(escape(flow.getCondition()))
                .append("</bpmn:conditionExpression>\n")
                .append("    </bpmn:sequenceFlow>\n");
    }

    private Map<String, Box> layout(AiWorkflowPlan plan) {
        Map<String, Box> boxes = new LinkedHashMap<>();
        int x = 120;
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            int width = nodeSize(node.getType());
            int height = nodeHeight(node.getType());
            boxes.put(node.getId(), new Box(x, 120, width, height));
            x += width + 100;
        }
        return boxes;
    }

    private void appendShape(StringBuilder xml, AiWorkflowPlan.Node node, Box box) {
        xml.append("      <bpmndi:BPMNShape id=\"Shape_").append(escape(node.getId()))
                .append("\" bpmnElement=\"").append(escape(node.getId())).append("\">\n")
                .append("        <dc:Bounds x=\"").append(box.x()).append("\" y=\"").append(box.y())
                .append("\" width=\"").append(box.width()).append("\" height=\"").append(box.height())
                .append("\"/>\n")
                .append("      </bpmndi:BPMNShape>\n");
    }

    private void appendEdge(StringBuilder xml, AiWorkflowPlan.Flow flow, Map<String, Box> boxes) {
        Box source = boxes.get(flow.getSourceRef());
        Box target = boxes.get(flow.getTargetRef());
        xml.append("      <bpmndi:BPMNEdge id=\"Edge_").append(escape(flow.getId()))
                .append("\" bpmnElement=\"").append(escape(flow.getId())).append("\">\n")
                .append("        <di:waypoint x=\"").append(source.right()).append("\" y=\"")
                .append(source.centerY()).append("\"/>\n")
                .append("        <di:waypoint x=\"").append(target.x()).append("\" y=\"")
                .append(target.centerY()).append("\"/>\n")
                .append("      </bpmndi:BPMNEdge>\n");
    }

    private String parameterValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private boolean validId(String value) {
        return StringUtils.isNotBlank(value) && xmlIdPattern.matcher(value).matches();
    }

    private String beanName(String componentId) {
        int separator = componentId.indexOf('_');
        return separator >= 0 ? componentId.substring(separator + 1) : componentId;
    }

    private int nodeSize(String type) {
        return switch (type) {
            case "startEvent", "endEvent" -> 36;
            case "exclusiveGateway" -> 50;
            default -> 120;
        };
    }

    private int nodeHeight(String type) {
        return switch (type) {
            case "startEvent", "endEvent" -> 36;
            case "exclusiveGateway" -> 50;
            default -> 80;
        };
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record Box(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int centerY() {
            return y + height / 2;
        }
    }
}
