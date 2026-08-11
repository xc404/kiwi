package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将受限 Plan IR 确定性编译为 Kiwi 可导入的 BPMN XML。
 * 组件绑定优先经 {@link AssistantComponentLookup} 解析；Catalog 仅作可选回退（测试/兼容）。
 */
@Component
public class AssistantPlanCompiler {

    private static final String KiwiXmlns = "http://kiwi.com/bpmn";

    private final Pattern xmlIdPattern = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private final Set<String> supportedTypes = Set.of(
            "startEvent", "endEvent", "serviceTask", "userTask", "exclusiveGateway");

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AssistantComponentLookup> componentLookupProvider;

    @Autowired
    public AssistantPlanCompiler(
            ObjectMapper objectMapper,
            ObjectProvider<AssistantComponentLookup> componentLookupProvider) {
        this.objectMapper = objectMapper;
        this.componentLookupProvider = componentLookupProvider;
    }

    /** 测试用：无 Lookup。 */
    public AssistantPlanCompiler(ObjectMapper objectMapper) {
        this(objectMapper, new EmptyLookupProvider());
    }

    public Optional<String> compile(String planIrJson) {
        return compile(planIrJson, null);
    }

    public Optional<String> compile(String planIrJson, String catalogJson) {
        if (StringUtils.isBlank(planIrJson)) {
            return Optional.empty();
        }
        try {
            AssistantPlan plan = objectMapper.readValue(planIrJson, AssistantPlan.class);
            AssistantCatalog catalog = null;
            if (StringUtils.isNotBlank(catalogJson)) {
                catalog = objectMapper.readValue(catalogJson, AssistantCatalog.class);
            }
            return Optional.of(compile(plan, catalog));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public String compile(AssistantPlan plan) {
        return compile(plan, null);
    }

    public String compile(AssistantPlan plan, AssistantCatalog catalog) {
        validatePlan(plan);
        Map<String, AssistantCatalog.CatalogComponent> catalogIndex = componentIndex(catalog);
        Map<String, Box> boxes = layout(plan);
        String processId = StringUtils.defaultIfBlank(plan.getProcessId(), "ai_generated_process");

        StringBuilder nodes = new StringBuilder();
        for (AssistantPlan.Node node : plan.getNodes()) {
            appendNode(nodes, node, catalogIndex);
        }
        StringBuilder flows = new StringBuilder();
        for (AssistantPlan.Flow flow : plan.getFlows()) {
            appendFlow(flows, flow);
        }
        StringBuilder shapes = new StringBuilder();
        for (AssistantPlan.Node node : plan.getNodes()) {
            appendShape(shapes, node, boxes.get(node.getId()));
        }
        StringBuilder edges = new StringBuilder();
        for (AssistantPlan.Flow flow : plan.getFlows()) {
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
                                  xmlns:kiwi="%s"
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
                KiwiXmlns,
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

    private void validatePlan(AssistantPlan plan) {
        if (plan == null || plan.getNodes() == null || plan.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Plan IR 缺少 nodes");
        }
        if (StringUtils.isNotBlank(plan.getProcessId()) && !validId(plan.getProcessId())) {
            throw new IllegalArgumentException("processId 非法");
        }
        Set<String> ids = new LinkedHashSet<>();
        int starts = 0;
        int ends = 0;
        for (AssistantPlan.Node node : plan.getNodes()) {
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
        for (AssistantPlan.Flow flow : plan.getFlows()) {
            if (flow == null || !validId(flow.getId()) || !flowIds.add(flow.getId())
                    || !ids.contains(flow.getSourceRef()) || !ids.contains(flow.getTargetRef())) {
                throw new IllegalArgumentException("Plan IR 连线非法");
            }
        }
    }

    private Map<String, AssistantCatalog.CatalogComponent> componentIndex(AssistantCatalog catalog) {
        Map<String, AssistantCatalog.CatalogComponent> result = new LinkedHashMap<>();
        if (catalog == null) {
            return result;
        }
        putComponents(result, catalog.getInstallable());
        putComponents(result, catalog.getInstalled());
        return result;
    }

    private void putComponents(
            Map<String, AssistantCatalog.CatalogComponent> target,
            List<AssistantCatalog.CatalogComponent> components) {
        if (components == null) {
            return;
        }
        for (AssistantCatalog.CatalogComponent component : components) {
            if (component != null && StringUtils.isNotBlank(component.getId())) {
                target.put(component.getId(), component);
                String alt = AssistantComponentIdAliases.alternateId(component.getId());
                if (alt != null) {
                    target.putIfAbsent(alt, component);
                }
            }
        }
    }

    private void appendNode(
            StringBuilder xml,
            AssistantPlan.Node node,
            Map<String, AssistantCatalog.CatalogComponent> catalogIndex) {
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
            case "serviceTask" -> appendServiceTask(xml, node, catalogIndex, id, name);
            default -> throw new IllegalArgumentException("不支持的节点类型: " + node.getType());
        }
    }

    private void appendServiceTask(
            StringBuilder xml,
            AssistantPlan.Node node,
            Map<String, AssistantCatalog.CatalogComponent> catalogIndex,
            String id,
            String nameAttribute) {
        String componentId = node.getComponentId();
        if (StringUtils.isBlank(componentId)) {
            throw new IllegalArgumentException("serviceTask 缺少 componentId");
        }
        String delegate = resolveDelegate(componentId, catalogIndex);
        String emittedId = resolveEmittedComponentId(componentId, catalogIndex);
        xml.append("    <bpmn:serviceTask id=\"").append(id).append("\"")
                .append(nameAttribute)
                .append(" camunda:delegateExpression=\"").append(escape(delegate)).append("\"")
                .append(" kiwi:componentId=\"").append(escape(emittedId)).append("\"");
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

    private String resolveDelegate(
            String componentId, Map<String, AssistantCatalog.CatalogComponent> catalogIndex) {
        AssistantComponentLookup lookup = componentLookupProvider.getIfAvailable();
        if (lookup != null) {
            Optional<String> fromLookup = lookup.resolveDelegateExpression(componentId);
            if (fromLookup.isPresent()) {
                return fromLookup.get();
            }
            if (lookup.pluginMissingHint(componentId).isPresent()) {
                return "${" + AssistantComponentIdAliases.beanName(componentId) + "}";
            }
        }
        AssistantCatalog.CatalogComponent fromCatalog = findInCatalog(componentId, catalogIndex);
        if (fromCatalog != null) {
            return StringUtils.defaultIfBlank(
                    fromCatalog.getDelegateExpression(),
                    "${" + AssistantComponentIdAliases.beanName(fromCatalog.getId()) + "}");
        }
        if (lookup == null && !catalogIndex.isEmpty()) {
            throw new IllegalArgumentException("componentId 无法解析: " + componentId);
        }
        // 无 Lookup 且无 Catalog：测试/离线仍允许用默认 bean 名编译，由校验阶段裁决
        if (lookup == null) {
            return "${" + AssistantComponentIdAliases.beanName(componentId) + "}";
        }
        throw new IllegalArgumentException("componentId 无法解析: " + componentId);
    }

    private String resolveEmittedComponentId(
            String componentId, Map<String, AssistantCatalog.CatalogComponent> catalogIndex) {
        AssistantCatalog.CatalogComponent fromCatalog = findInCatalog(componentId, catalogIndex);
        if (fromCatalog != null && StringUtils.isNotBlank(fromCatalog.getId())) {
            return fromCatalog.getId();
        }
        return componentId;
    }

    private AssistantCatalog.CatalogComponent findInCatalog(
            String componentId, Map<String, AssistantCatalog.CatalogComponent> catalogIndex) {
        if (catalogIndex == null || catalogIndex.isEmpty()) {
            return null;
        }
        AssistantCatalog.CatalogComponent direct = catalogIndex.get(componentId);
        if (direct != null) {
            return direct;
        }
        String alt = AssistantComponentIdAliases.alternateId(componentId);
        return alt != null ? catalogIndex.get(alt) : null;
    }

    private void appendFlow(StringBuilder xml, AssistantPlan.Flow flow) {
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

    private Map<String, Box> layout(AssistantPlan plan) {
        Map<String, Box> boxes = new LinkedHashMap<>();
        int x = 120;
        for (AssistantPlan.Node node : plan.getNodes()) {
            int width = nodeSize(node.getType());
            int height = nodeHeight(node.getType());
            boxes.put(node.getId(), new Box(x, 120, width, height));
            x += width + 100;
        }
        return boxes;
    }

    private void appendShape(StringBuilder xml, AssistantPlan.Node node, Box box) {
        xml.append("      <bpmndi:BPMNShape id=\"Shape_").append(escape(node.getId()))
                .append("\" bpmnElement=\"").append(escape(node.getId())).append("\">\n")
                .append("        <dc:Bounds x=\"").append(box.x()).append("\" y=\"").append(box.y())
                .append("\" width=\"").append(box.width()).append("\" height=\"").append(box.height())
                .append("\"/>\n")
                .append("      </bpmndi:BPMNShape>\n");
    }

    private void appendEdge(StringBuilder xml, AssistantPlan.Flow flow, Map<String, Box> boxes) {
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

    private static final class EmptyLookupProvider implements ObjectProvider<AssistantComponentLookup> {
        @Override
        public AssistantComponentLookup getObject() {
            return null;
        }

        @Override
        public AssistantComponentLookup getIfAvailable() {
            return null;
        }

        @Override
        public AssistantComponentLookup getIfUnique() {
            return null;
        }
    }
}
