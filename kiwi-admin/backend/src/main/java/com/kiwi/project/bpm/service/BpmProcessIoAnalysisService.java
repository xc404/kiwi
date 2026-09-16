package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.KiwiBpmnXml;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.model.BpmProcessIoGapAnalysis;
import com.kiwi.project.bpm.model.BpmProcessIoInventory;
import com.kiwi.project.bpm.model.BpmProcessIoInventory.Direction;
import com.kiwi.project.bpm.model.BpmProcessIoInventory.Param;
import com.kiwi.project.bpm.model.BpmProcessIoInventory.StartVariable;
import com.kiwi.project.bpm.model.BpmProcessIoInventory.ValueKind;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN 流程级与组件级输入/输出分析。
 */
@Service
@RequiredArgsConstructor
public class BpmProcessIoAnalysisService {

    private static final String BpmnNs = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CamundaNs = "http://camunda.org/schema/1.0/bpmn";
    /** 与运行时 AssignmentActivity 等一致：仅一层 ${varName} */
    private static final Pattern InputVarRef = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    private final BpmComponentService bpmComponentService;

    public BpmProcessIoInventory analyzeInventory(String bpmnXml) {
        if (StringUtils.isBlank(bpmnXml)) {
            throw new IllegalArgumentException("bpmnXml 不能为空");
        }
        Document doc = parseDocument(bpmnXml);
        Map<String, List<String>> reverseAdj = buildReverseAdjacency(doc);
        Map<String, List<String>> forwardAdj = buildForwardAdjacency(doc);
        List<Element> tasks = findComponentTasks(doc);

        Map<String, Element> taskById = new LinkedHashMap<>();
        Map<String, String> componentIdByTask = new LinkedHashMap<>();
        Map<String, BpmComponent> componentByTaskId = new HashMap<>();
        for (Element task : tasks) {
            String taskId = StringUtils.trimToNull(task.getAttribute("id"));
            if (taskId == null) {
                continue;
            }
            String componentId = componentIdOf(task);
            if (StringUtils.isBlank(componentId)) {
                continue;
            }
            taskById.put(taskId, task);
            componentIdByTask.put(taskId, componentId.trim());
            BpmComponent resolved = bpmComponentService.resolveComponentById(componentId.trim());
            if (resolved != null) {
                componentByTaskId.put(taskId, resolved);
            }
        }

        List<String> orderedTaskIds = orderTasksByFlow(forwardAdj, taskById.keySet());
        BpmProcessIoInventory inventory = new BpmProcessIoInventory();
        LinkedHashMap<String, StartVariable> startByKey = new LinkedHashMap<>();
        LinkedHashMap<String, BpmComponentParameter> outputByKey = new LinkedHashMap<>();

        for (String taskId : orderedTaskIds) {
            Element task = taskById.get(taskId);
            BpmComponent component = componentByTaskId.get(taskId);
            Set<String> upstreamOutputKeys = collectUpstreamOutputKeys(
                    reverseAdj, taskId, taskById, componentByTaskId);

            BpmProcessIoInventory.Node node = new BpmProcessIoInventory.Node();
            node.setNodeId(taskId);
            node.setName(StringUtils.trimToNull(task.getAttribute("name")));
            node.setComponentId(componentIdByTask.get(taskId));
            if (component != null) {
                node.setComponentName(component.getName());
            }
            Map<String, String> xmlInputs = collectNamedParameters(task, "inputParameter");
            Map<String, String> xmlOutputs = collectNamedParameters(task, "outputParameter");
            node.setInputs(buildInputs(component, xmlInputs, upstreamOutputKeys));
            node.setOutputs(buildOutputs(component, xmlOutputs));
            inventory.getNodes().add(node);

            for (Param input : node.getInputs()) {
                collectStartVariables(startByKey, taskId, input, upstreamOutputKeys);
            }
            mergeProcessOutputs(outputByKey, component);
        }

        inventory.setStartVariables(new ArrayList<>(startByKey.values()));
        inventory.setProcessOutputs(new ArrayList<>(outputByKey.values()));
        return inventory;
    }

    /**
     * 流程级输入/输出汇总，由 {@link #analyzeInventory(String)} 派生。
     */
    public BpmProcessIoGapAnalysis analyzeComponentIoGaps(String bpmnXml) {
        BpmProcessIoInventory inventory = analyzeInventory(bpmnXml);
        BpmProcessIoGapAnalysis result = new BpmProcessIoGapAnalysis();
        List<BpmComponentParameter> processInputs = new ArrayList<>();
        for (StartVariable start : inventory.getStartVariables()) {
            processInputs.add(toProcessInputParameter(start));
        }
        result.setProcessInputs(processInputs);
        result.setProcessOutputs(new ArrayList<>(inventory.getProcessOutputs()));
        return result;
    }

    /**
     * 将流程定义包装为逻辑 {@link BpmComponent}：输入/输出与缺口分析一致。
     */
    public BpmComponent wrapProcessAsComponent(BpmProcess process) {
        if (process == null) {
            throw new IllegalArgumentException("process 不能为空");
        }
        if (StringUtils.isBlank(process.getBpmnXml())) {
            throw new IllegalArgumentException("流程 BPMN 为空");
        }
        BpmProcessIoGapAnalysis gap = analyzeComponentIoGaps(process.getBpmnXml());
        BpmComponent c = new BpmComponent();
        c.setId(process.getId());
        c.setKey("process:" + process.getId());
        c.setName(StringUtils.isNotBlank(process.getName()) ? process.getName() : process.getId());
        c.setDescription("流程定义 " + process.getId() + " 的输入/输出分析结果");
        c.setGroup("公共流程");
        c.setSource(BpmProcessDefinitionService.XBPM);
        c.setType(BpmComponent.Type.CallActivity);
        c.setInputParameters(new ArrayList<>(gap.getProcessInputs()));
        c.setOutputParameters(new ArrayList<>(gap.getProcessOutputs()));
        c.setVersion("1.0");
        return c;
    }

    private List<Param> buildInputs(
            BpmComponent component,
            Map<String, String> xmlInputs,
            Set<String> upstreamOutputKeys) {
        List<Param> inputs = new ArrayList<>();
        Set<String> catalogKeys = new HashSet<>();
        if (component != null && component.getInputParameters() != null) {
            for (BpmComponentParameter p : component.getInputParameters()) {
                if (p == null || p.isHidden() || StringUtils.isBlank(p.getKey())) {
                    continue;
                }
                String key = p.getKey().trim();
                catalogKeys.add(key);
                String configured = xmlInputs.get(key);
                inputs.add(toInputParam(p, configured, true, upstreamOutputKeys));
            }
        }
        for (Map.Entry<String, String> extra : xmlInputs.entrySet()) {
            if (catalogKeys.contains(extra.getKey())) {
                continue;
            }
            BpmComponentParameter synthetic = new BpmComponentParameter();
            synthetic.setKey(extra.getKey());
            synthetic.setName(extra.getKey());
            inputs.add(toInputParam(synthetic, extra.getValue(), false, upstreamOutputKeys));
        }
        return inputs;
    }

    private Param toInputParam(
            BpmComponentParameter catalog,
            String configuredRaw,
            boolean fromCatalog,
            Set<String> upstreamOutputKeys) {
        String configured = configuredRaw == null ? null : configuredRaw.trim();
        boolean filled = StringUtils.isNotBlank(configured);
        List<String> refs = extractVariableRefs(configured);
        boolean implicitRequired = fromCatalog && catalog.isRequired() && !filled;
        if (implicitRequired) {
            refs = List.of(catalog.getKey().trim());
        }
        Param param = new Param();
        param.setKey(catalog.getKey().trim());
        param.setName(StringUtils.defaultIfBlank(catalog.getName(), catalog.getKey()));
        param.setDescription(catalog.getDescription());
        param.setRequired(catalog.isRequired());
        param.setDirection(Direction.Input);
        param.setFilled(filled);
        param.setConfiguredValue(filled ? configured : null);
        param.setValueKind(classifyValue(configured, implicitRequired));
        param.setExpressionRefs(new ArrayList<>(refs));
        param.setSatisfiedByUpstream(allRefsProduced(refs, upstreamOutputKeys));
        return param;
    }

    private List<Param> buildOutputs(BpmComponent component, Map<String, String> xmlOutputs) {
        List<Param> outputs = new ArrayList<>();
        Set<String> catalogKeys = new HashSet<>();
        if (component != null && component.getOutputParameters() != null) {
            for (BpmComponentParameter p : component.getOutputParameters()) {
                if (p == null || p.isHidden() || StringUtils.isBlank(p.getKey())) {
                    continue;
                }
                String key = p.getKey().trim();
                catalogKeys.add(key);
                String processVariable = StringUtils.defaultIfBlank(p.getDefaultValue(), key);
                Param param = new Param();
                param.setKey(key);
                param.setName(StringUtils.defaultIfBlank(p.getName(), key));
                param.setRequired(p.isRequired());
                param.setDirection(Direction.Output);
                param.setFilled(true);
                param.setValueKind(ValueKind.Literal);
                param.setConfiguredValue(xmlOutputs.get(key));
                param.setProcessVariable(processVariable);
                param.setSatisfiedByUpstream(false);
                outputs.add(param);
            }
        }
        for (Map.Entry<String, String> extra : xmlOutputs.entrySet()) {
            if (catalogKeys.contains(extra.getKey())) {
                continue;
            }
            Param param = new Param();
            param.setKey(extra.getKey());
            param.setName(extra.getKey());
            param.setDirection(Direction.Output);
            param.setFilled(true);
            param.setValueKind(classifyValue(extra.getValue(), false));
            param.setConfiguredValue(extra.getValue());
            param.setProcessVariable(extra.getKey());
            param.setExpressionRefs(extractVariableRefs(extra.getValue()));
            outputs.add(param);
        }
        return outputs;
    }

    private void collectStartVariables(
            LinkedHashMap<String, StartVariable> startByKey,
            String taskId,
            Param input,
            Set<String> upstreamOutputKeys) {
        if (input.getValueKind() == ValueKind.Literal) {
            return;
        }
        for (String ref : input.getExpressionRefs()) {
            if (StringUtils.isBlank(ref) || upstreamOutputKeys.contains(ref) || startByKey.containsKey(ref)) {
                continue;
            }
            StartVariable start = new StartVariable();
            start.setKey(ref);
            start.setName(ref.equals(input.getKey()) ? input.getName() : ref);
            start.setDescription(input.getDescription());
            start.setRequired(input.isRequired());
            start.setNodeId(taskId);
            start.setParameterKey(input.getKey());
            startByKey.put(ref, start);
        }
    }

    private void mergeProcessOutputs(LinkedHashMap<String, BpmComponentParameter> outputByKey, BpmComponent component) {
        if (component == null || component.getOutputParameters() == null) {
            return;
        }
        for (BpmComponentParameter p : component.getOutputParameters()) {
            if (p == null || p.isHidden() || StringUtils.isBlank(p.getKey())) {
                continue;
            }
            String k = p.getKey().trim();
            outputByKey.remove(k);
            outputByKey.put(k, copyParameter(p));
        }
    }

    private BpmComponentParameter toProcessInputParameter(StartVariable start) {
        BpmComponentParameter p = new BpmComponentParameter();
        p.setKey(start.getKey());
        p.setName(StringUtils.defaultIfBlank(start.getName(), start.getKey()));
        p.setDescription(start.getDescription());
        p.setRequired(start.isRequired());
        return p;
    }

    private BpmComponentParameter copyParameter(BpmComponentParameter p) {
        BpmComponentParameter c = new BpmComponentParameter();
        c.setKey(p.getKey());
        c.setName(p.getName());
        c.setDescription(p.getDescription());
        c.setDefaultValue(p.getDefaultValue());
        c.setArray(p.isArray());
        c.setRequired(p.isRequired());
        c.setReadonly(p.isReadonly());
        c.setHidden(p.isHidden());
        c.setHtmlType(p.getHtmlType());
        c.setType(p.getType());
        c.setExample(p.getExample());
        c.setDictKey(p.getDictKey());
        c.setGroup(p.getGroup());
        c.setImportant(p.isImportant());
        c.setAdditionalOption(p.getAdditionalOption());
        return c;
    }

    private ValueKind classifyValue(String configured, boolean implicitRequired) {
        if (implicitRequired || StringUtils.isBlank(configured)) {
            return ValueKind.Empty;
        }
        if (!extractVariableRefs(configured).isEmpty()) {
            return ValueKind.Expression;
        }
        return ValueKind.Literal;
    }

    private boolean allRefsProduced(List<String> refs, Set<String> upstreamOutputKeys) {
        if (refs == null || refs.isEmpty()) {
            return true;
        }
        for (String ref : refs) {
            if (!upstreamOutputKeys.contains(ref)) {
                return false;
            }
        }
        return true;
    }

    private Set<String> collectUpstreamOutputKeys(
            Map<String, List<String>> reverseAdj,
            String taskId,
            Map<String, Element> taskById,
            Map<String, BpmComponent> componentByTaskId) {
        Set<String> keys = new HashSet<>();
        Set<String> reachable = backwardReachable(reverseAdj, taskId);
        for (String predId : reachable) {
            if (predId.equals(taskId) || !taskById.containsKey(predId)) {
                continue;
            }
            BpmComponent up = componentByTaskId.get(predId);
            keys.addAll(collectProducedKeys(up, collectNamedParameters(taskById.get(predId), "outputParameter")));
        }
        return keys;
    }

    private Set<String> collectProducedKeys(BpmComponent component, Map<String, String> xmlOutputs) {
        Set<String> keys = new HashSet<>();
        if (component != null && component.getOutputParameters() != null) {
            for (BpmComponentParameter p : component.getOutputParameters()) {
                if (p == null || p.isHidden() || StringUtils.isBlank(p.getKey())) {
                    continue;
                }
                keys.add(p.getKey().trim());
            }
        }
        keys.addAll(xmlOutputs.keySet());
        return keys;
    }

    private Document parseDocument(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 BPMN XML: " + e.getMessage(), e);
        }
    }

    private String componentIdOf(Element el) {
        String fromNs = el.getAttributeNS(KiwiBpmnXml.Namespace, "componentId");
        if (StringUtils.isNotBlank(fromNs)) {
            return fromNs.trim();
        }
        String fromProperty = findCamundaPropertyValue(el, "componentId");
        if (StringUtils.isNotBlank(fromProperty)) {
            return fromProperty.trim();
        }
        String unprefixed = el.getAttribute("componentId");
        return StringUtils.isBlank(unprefixed) ? null : unprefixed.trim();
    }

    private List<String> orderTasksByFlow(Map<String, List<String>> forwardAdj, Set<String> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        Set<String> nodes = new HashSet<>();
        for (Map.Entry<String, List<String>> e : forwardAdj.entrySet()) {
            nodes.add(e.getKey());
            nodes.addAll(e.getValue());
        }
        Map<String, Integer> inDegree = new HashMap<>();
        for (String n : nodes) {
            inDegree.putIfAbsent(n, 0);
        }
        for (Map.Entry<String, List<String>> e : forwardAdj.entrySet()) {
            for (String t : e.getValue()) {
                inDegree.merge(t, 1, Integer::sum);
            }
        }
        Queue<String> q = new ArrayDeque<>();
        List<String> smallerFirst = new ArrayList<>(nodes);
        Collections.sort(smallerFirst);
        for (String n : smallerFirst) {
            if (inDegree.getOrDefault(n, 0) == 0) {
                q.add(n);
            }
        }
        List<String> topo = new ArrayList<>();
        while (!q.isEmpty()) {
            String u = q.poll();
            topo.add(u);
            for (String v : forwardAdj.getOrDefault(u, List.of())) {
                int d = inDegree.merge(v, -1, Integer::sum);
                if (d == 0) {
                    q.add(v);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String id : topo) {
            if (taskIds.contains(id)) {
                out.add(id);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String id : taskIds) {
            if (!out.contains(id)) {
                rest.add(id);
            }
        }
        Collections.sort(rest);
        out.addAll(rest);
        return out;
    }

    private Map<String, List<String>> buildReverseAdjacency(Document doc) {
        Map<String, List<String>> rev = new HashMap<>();
        appendSequenceFlows(doc, rev, true);
        return rev;
    }

    private Map<String, List<String>> buildForwardAdjacency(Document doc) {
        Map<String, List<String>> fwd = new HashMap<>();
        appendSequenceFlows(doc, fwd, false);
        return fwd;
    }

    private void appendSequenceFlows(Document doc, Map<String, List<String>> adj, boolean reverse) {
        NodeList flows = doc.getElementsByTagNameNS(BpmnNs, "sequenceFlow");
        boolean any = false;
        for (int i = 0; i < flows.getLength(); i++) {
            if (flows.item(i) instanceof Element flow) {
                any |= putFlow(adj, flow, reverse);
            }
        }
        if (!any) {
            appendSequenceFlowsByLocalName(doc.getDocumentElement(), adj, reverse);
        }
    }

    private boolean putFlow(Map<String, List<String>> adj, Element flow, boolean reverse) {
        String src = flow.getAttribute("sourceRef");
        String tgt = flow.getAttribute("targetRef");
        if (StringUtils.isAnyBlank(src, tgt)) {
            return false;
        }
        if (reverse) {
            adj.computeIfAbsent(tgt.trim(), k -> new ArrayList<>()).add(src.trim());
        } else {
            adj.computeIfAbsent(src.trim(), k -> new ArrayList<>()).add(tgt.trim());
        }
        return true;
    }

    private void appendSequenceFlowsByLocalName(Element root, Map<String, List<String>> adj, boolean reverse) {
        if (root == null) {
            return;
        }
        if ("sequenceFlow".equalsIgnoreCase(localName(root))) {
            putFlow(adj, root, reverse);
        }
        NodeList ch = root.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            if (ch.item(i) instanceof Element child) {
                appendSequenceFlowsByLocalName(child, adj, reverse);
            }
        }
    }

    private Set<String> backwardReachable(Map<String, List<String>> reverseAdj, String nodeId) {
        Set<String> seen = new HashSet<>();
        Queue<String> q = new ArrayDeque<>();
        q.add(nodeId);
        seen.add(nodeId);
        while (!q.isEmpty()) {
            String n = q.poll();
            for (String pred : reverseAdj.getOrDefault(n, List.of())) {
                if (seen.add(pred)) {
                    q.add(pred);
                }
            }
        }
        return seen;
    }

    private Map<String, String> collectNamedParameters(Element task, String localName) {
        Map<String, String> values = new LinkedHashMap<>();
        NodeList byNs = task.getElementsByTagNameNS(CamundaNs, localName);
        putNamedParameters(byNs, values);
        if (!values.isEmpty()) {
            return values;
        }
        NodeList all = task.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element el)) {
                continue;
            }
            if (localName.equalsIgnoreCase(localName(el))) {
                String name = el.getAttribute("name");
                if (StringUtils.isNotBlank(name) && !values.containsKey(name.trim())) {
                    values.put(name.trim(), StringUtils.defaultString(el.getTextContent()));
                }
            }
        }
        return values;
    }

    private void putNamedParameters(NodeList nl, Map<String, String> values) {
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element el) {
                String name = el.getAttribute("name");
                if (StringUtils.isNotBlank(name) && !values.containsKey(name.trim())) {
                    values.put(name.trim(), StringUtils.defaultString(el.getTextContent()));
                }
            }
        }
    }

    private List<String> extractVariableRefs(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Matcher m = InputVarRef.matcher(text);
        while (m.find()) {
            unique.add(m.group(1));
        }
        return new ArrayList<>(unique);
    }

    private String findCamundaPropertyValue(Element scope, String propertyName) {
        NodeList byNs = scope.getElementsByTagNameNS(CamundaNs, "property");
        for (int i = 0; i < byNs.getLength(); i++) {
            if (byNs.item(i) instanceof Element el && propertyName.equals(el.getAttribute("name"))) {
                return el.getAttribute("value");
            }
        }
        NodeList all = scope.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element el)) {
                continue;
            }
            if ("property".equalsIgnoreCase(localName(el)) && propertyName.equals(el.getAttribute("name"))) {
                return el.getAttribute("value");
            }
        }
        NamedNodeMap attributes = scope.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if ("componentId".equals(attribute.getLocalName())
                        || "componentId".equals(attribute.getNodeName())) {
                    if ("componentId".equals(propertyName)) {
                        return attribute.getNodeValue();
                    }
                }
            }
        }
        return null;
    }

    private List<Element> findComponentTasks(Document doc) {
        List<Element> out = new ArrayList<>();
        collectByNs(doc, "serviceTask", out);
        collectByNs(doc, "callActivity", out);
        if (!out.isEmpty()) {
            return out;
        }
        collectByLocalName(doc.getDocumentElement(), "serviceTask", out);
        collectByLocalName(doc.getDocumentElement(), "callActivity", out);
        return out;
    }

    private void collectByNs(Document doc, String local, List<Element> out) {
        NodeList ns = doc.getElementsByTagNameNS(BpmnNs, local);
        for (int i = 0; i < ns.getLength(); i++) {
            out.add((Element) ns.item(i));
        }
    }

    private void collectByLocalName(Element el, String wantLocal, List<Element> out) {
        if (wantLocal.equalsIgnoreCase(localName(el))) {
            out.add(el);
        }
        NodeList ch = el.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            if (ch.item(i) instanceof Element child) {
                collectByLocalName(child, wantLocal, out);
            }
        }
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
}
