package com.kiwi.bpmn.designer.harness.bpmn;

import com.kiwi.project.ai.authoring.AiWorkflowPlan;
import com.kiwi.project.bpm.KiwiBpmnXml;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 BPMN XML 收成 {@link AiWorkflowPlan}。第一版只保留 start/end/serviceTask/userTask/exclusiveGateway 与 sequenceFlow。
 */
@Component
public class DesignerHarnessBpmnReader {

    static final String BpmnNs = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    static final String CamundaNs = "http://camunda.org/schema/1.0/bpmn";

    private static final Set<String> SupportedNodes = Set.of(
            "startEvent", "endEvent", "serviceTask", "userTask", "exclusiveGateway");
    private static final Set<String> SkippedProcessChildren = Set.of(
            "documentation", "extensionElements", "incoming", "outgoing", "property");

    public ParseResult read(String bpmnXml) {
        ParseResult result = new ParseResult();
        if (StringUtils.isBlank(bpmnXml)) {
            result.warnings.add("BPMN XML 为空");
            return result;
        }
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            result.warnings.add("无法解析 BPMN XML: " + e.getMessage());
            return result;
        }
        Element process = firstProcess(doc);
        if (process == null) {
            result.warnings.add("文档中没有 bpmn:process");
            return result;
        }
        AiWorkflowPlan plan = new AiWorkflowPlan();
        plan.setProcessId(process.getAttribute("id"));
        plan.setName(process.getAttribute("name"));
        NodeList children = process.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element el)) {
                continue;
            }
            String local = localName(el);
            if (SkippedProcessChildren.contains(local)) {
                continue;
            }
            if (SupportedNodes.contains(local)) {
                plan.getNodes().add(toNode(el, local));
            } else if ("sequenceFlow".equals(local)) {
                plan.getFlows().add(toFlow(el));
            } else {
                result.warnings.add("已忽略不支持的元素: " + local);
            }
        }
        result.plan = plan;
        return result;
    }

    private Element firstProcess(Document doc) {
        NodeList list = doc.getElementsByTagNameNS(BpmnNs, "process");
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        return null;
    }

    private AiWorkflowPlan.Node toNode(Element el, String type) {
        AiWorkflowPlan.Node node = new AiWorkflowPlan.Node();
        node.setId(el.getAttribute("id"));
        node.setType(type);
        node.setName(el.getAttribute("name"));
        if ("serviceTask".equals(type)) {
            node.setComponentId(componentIdOf(el));
            node.setParameters(inputParameters(el));
        }
        return node;
    }

    private AiWorkflowPlan.Flow toFlow(Element el) {
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId(el.getAttribute("id"));
        flow.setSourceRef(el.getAttribute("sourceRef"));
        flow.setTargetRef(el.getAttribute("targetRef"));
        NodeList conditions = el.getElementsByTagNameNS(BpmnNs, "conditionExpression");
        if (conditions.getLength() > 0) {
            flow.setCondition(conditions.item(0).getTextContent());
        }
        return flow;
    }

    private Map<String, Object> inputParameters(Element task) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        NodeList params = task.getElementsByTagNameNS(CamundaNs, "inputParameter");
        for (int i = 0; i < params.getLength(); i++) {
            Element p = (Element) params.item(i);
            String name = p.getAttribute("name");
            if (StringUtils.isNotBlank(name)) {
                parameters.put(name, p.getTextContent());
            }
        }
        return parameters;
    }

    private String componentIdOf(Element el) {
        String fromNs = el.getAttributeNS(KiwiBpmnXml.Namespace, "componentId");
        if (StringUtils.isNotBlank(fromNs)) {
            return fromNs;
        }
        String fromProperty = camundaProperty(el, "componentId");
        if (StringUtils.isNotBlank(fromProperty)) {
            return fromProperty;
        }
        String unprefixed = el.getAttribute("componentId");
        return StringUtils.isBlank(unprefixed) ? null : unprefixed;
    }

    private String camundaProperty(Element scope, String propertyName) {
        NodeList props = scope.getElementsByTagNameNS(CamundaNs, "property");
        for (int i = 0; i < props.getLength(); i++) {
            Element p = (Element) props.item(i);
            if (propertyName.equals(p.getAttribute("name"))) {
                return p.getAttribute("value");
            }
        }
        return null;
    }

    private String localName(Element el) {
        String ln = el.getLocalName();
        if (ln != null) {
            return ln;
        }
        String tag = el.getTagName();
        int i = tag.indexOf(':');
        return i >= 0 ? tag.substring(i + 1) : tag;
    }

    public static class ParseResult {
        public AiWorkflowPlan plan = new AiWorkflowPlan();
        public List<String> warnings = new ArrayList<>();
    }
}