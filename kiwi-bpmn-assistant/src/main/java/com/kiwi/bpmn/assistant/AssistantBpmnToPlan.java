package com.kiwi.bpmn.assistant;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 将 BPMN XML 解析为 {@link AssistantPlan}（modify 模式的 basePlanIr）。
 */
@Component
public class AssistantBpmnToPlan {

    public Optional<AssistantPlan> parse(String bpmnXml) {
        if (StringUtils.isBlank(bpmnXml)) {
            return Optional.empty();
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            return Optional.of(toPlan(doc));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private AssistantPlan toPlan(Document doc) {
        AssistantPlan plan = new AssistantPlan();
        NodeList processes = doc.getElementsByTagNameNS("*", "process");
        if (processes.getLength() > 0 && processes.item(0) instanceof Element process) {
            plan.setProcessId(process.getAttribute("id"));
            plan.setName(process.getAttribute("name"));
        }
        Map<String, Element> byId = indexById(doc);
        for (Element el : byId.values()) {
            String type = mapType(el.getLocalName());
            if (type == null) {
                continue;
            }
            AssistantPlan.Node node = new AssistantPlan.Node();
            node.setId(el.getAttribute("id"));
            node.setType(type);
            if (StringUtils.isNotBlank(el.getAttribute("name"))) {
                node.setName(el.getAttribute("name"));
            }
            if ("serviceTask".equals(type)) {
                node.setComponentId(componentId(el));
                node.setParameters(inputParameters(el));
            }
            plan.getNodes().add(node);
        }
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            if (!(flows.item(i) instanceof Element flowEl)) {
                continue;
            }
            AssistantPlan.Flow flow = new AssistantPlan.Flow();
            flow.setId(flowEl.getAttribute("id"));
            flow.setSourceRef(flowEl.getAttribute("sourceRef"));
            flow.setTargetRef(flowEl.getAttribute("targetRef"));
            NodeList conditions = flowEl.getElementsByTagNameNS("*", "conditionExpression");
            if (conditions.getLength() > 0) {
                flow.setCondition(conditions.item(0).getTextContent());
            }
            plan.getFlows().add(flow);
        }
        return plan;
    }

    private String mapType(String localName) {
        if (localName == null) {
            return null;
        }
        return switch (localName) {
            case "startEvent", "endEvent", "serviceTask", "userTask", "exclusiveGateway" -> localName;
            default -> null;
        };
    }

    private String componentId(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if ("componentId".equals(attribute.getLocalName())
                    || "componentId".equals(attribute.getNodeName())) {
                return attribute.getNodeValue();
            }
        }
        return null;
    }

    private Map<String, Object> inputParameters(Element componentElement) {
        Map<String, Object> params = new LinkedHashMap<>();
        NodeList descendants = componentElement.getElementsByTagNameNS("*", "inputParameter");
        for (int i = 0; i < descendants.getLength(); i++) {
            if (descendants.item(i) instanceof Element input) {
                String name = input.getAttribute("name");
                if (StringUtils.isNotBlank(name)) {
                    params.put(name, input.getTextContent());
                }
            }
        }
        return params;
    }

    private Map<String, Element> indexById(Document doc) {
        Map<String, Element> map = new LinkedHashMap<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element el) {
                String id = el.getAttribute("id");
                String local = el.getLocalName();
                if (StringUtils.isNotBlank(id) && mapType(local) != null) {
                    map.put(id, el);
                }
            }
        }
        return map;
    }
}
