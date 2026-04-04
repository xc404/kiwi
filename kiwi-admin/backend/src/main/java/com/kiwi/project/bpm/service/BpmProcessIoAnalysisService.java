package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.model.BpmProcessIoGapAnalysis;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 * BPMN 流程级输入/输出（组件元数据与连线）分析。
 */
@Service
@RequiredArgsConstructor
public class BpmProcessIoAnalysisService
{
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    /** 与运行时 AssignmentActivity 等一致：仅一层 ${varName} */
    private static final Pattern INPUT_VAR_REF = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    private final BpmComponentService bpmComponentService;

    /**
     * 分析整个流程的「流程级输入」需求：
     * <ul>
     *   <li>组件任务 {@code camunda:inputParameter} 的值中出现 {@code ${var}}，表示执行该任务时需要流程变量 {@code var}。</li>
     *   <li>若控制流上存在<strong>上游</strong>组件任务，其元数据 {@code outputParameters} 中含有同名 {@code key}，
     *       则视为该变量已由上游产出，不再算作流程启动时需注入的变量。</li>
     *   <li>上游：沿 {@code sequenceFlow} 反向从当前任务可达的任意节点（不含自身）上的其它组件任务。</li>
     *   <li>流程输出：所有已解析组件的 {@code outputParameters} 按控制流拓扑顺序合并，同名 key 以后出现的组件为准。</li>
     * </ul>
     *
     * @param bpmnXml 完整 BPMN 2.0 文档
     */
    public BpmProcessIoGapAnalysis analyzeComponentIoGaps(String bpmnXml) {
        if (StringUtils.isBlank(bpmnXml)) {
            throw new IllegalArgumentException("bpmnXml 不能为空");
        }
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 BPMN XML: " + e.getMessage(), e);
        }

        Map<String, List<String>> reverseAdj = buildReverseAdjacency(doc);
        Map<String, List<String>> forwardAdj = buildForwardAdjacency(doc);
        List<Element> serviceTasks = findServiceTasks(doc);
        Map<String, BpmComponent> componentByTaskId = new HashMap<>();
        for (Element task : serviceTasks) {
            String cid = findCamundaPropertyValue(task, "componentId");
            if (StringUtils.isBlank(cid)) {
                continue;
            }
            BpmComponent c = bpmComponentService.resolveComponentById(cid.trim());
            if (c != null) {
                componentByTaskId.put(task.getAttribute("id"), c);
            }
        }

        LinkedHashMap<String, BpmComponentParameter> processInputByVar = new LinkedHashMap<>();
        for (Element task : serviceTasks) {
            String componentId = findCamundaPropertyValue(task, "componentId");
            if (StringUtils.isBlank(componentId)) {
                continue;
            }
            String taskId = task.getAttribute("id");
            BpmComponent self = componentByTaskId.get(taskId);
            String inputText = collectInputParameterText(task);
            List<String> refs = extractVariableRefs(inputText);

            Set<String> upstreamOutputKeys = new HashSet<>();
            Set<String> predAll = backwardReachable(reverseAdj, taskId);
            for (String pid : predAll) {
                if (pid.equals(taskId)) {
                    continue;
                }
                BpmComponent up = componentByTaskId.get(pid);
                if (up != null) {
                    upstreamOutputKeys.addAll(collectOutputKeys(up));
                }
            }

            for (String v : refs) {
                if (upstreamOutputKeys.contains(v) || processInputByVar.containsKey(v)) {
                    continue;
                }
                processInputByVar.put(v, resolveProcessInputDescriptor(self, task, v));
            }
        }

        List<String> orderedTaskIds = orderServiceTasksByFlow(forwardAdj, componentByTaskId.keySet());
        LinkedHashMap<String, BpmComponentParameter> outputByKey = new LinkedHashMap<>();
        for (String tid : orderedTaskIds) {
            BpmComponent comp = componentByTaskId.get(tid);
            if (comp == null || comp.getOutputParameters() == null) {
                continue;
            }
            for (BpmComponentParameter p : comp.getOutputParameters()) {
                if (p == null || p.isHidden() || StringUtils.isBlank(p.getKey())) {
                    continue;
                }
                String k = p.getKey().trim();
                outputByKey.remove(k);
                outputByKey.put(k, p);
            }
        }

        BpmProcessIoGapAnalysis result = new BpmProcessIoGapAnalysis();
        result.setProcessInputs(new ArrayList<>(processInputByVar.values()));
        result.setProcessOutputs(new ArrayList<>(outputByKey.values()));
        return result;
    }

    /**
     * 将流程定义包装为「逻辑 {@link BpmComponent}」：{@code inputParameters}/{@code outputParameters} 与
     * {@link #analyzeComponentIoGaps(String)} 结果一致，供编排/文档展示；非可执行 Spring Bean。
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
        c.setType(BpmComponent.Type.SpringBean);
        c.setInputParameters(new ArrayList<>(gap.getProcessInputs()));
        c.setOutputParameters(new ArrayList<>(gap.getProcessOutputs()));
        c.setVersion("1.0");
        return c;
    }

    /**
     * 为「须由流程注入」的变量名构造 {@link BpmComponentParameter}：优先匹配当前任务上引用该变量的输入项元数据。
     */
    private static BpmComponentParameter resolveProcessInputDescriptor(BpmComponent self, Element task, String varName) {
        if (self != null && self.getInputParameters() != null) {
            for (BpmComponentParameter p : self.getInputParameters()) {
                if (p == null || p.isHidden()) {
                    continue;
                }
                if (StringUtils.isNotBlank(p.getKey()) && varName.equals(p.getKey().trim())) {
                    return copyBpmParameter(p);
                }
            }
            String paramKey = findInputParameterNameContainingVarRef(task, varName);
            if (StringUtils.isNotBlank(paramKey)) {
                for (BpmComponentParameter p : self.getInputParameters()) {
                    if (p == null || p.isHidden()) {
                        continue;
                    }
                    if (paramKey.equals(p.getKey())) {
                        return copyBpmParameter(p);
                    }
                }
            }
        }
        BpmComponentParameter synthetic = new BpmComponentParameter();
        synthetic.setKey(varName);
        synthetic.setName(varName);
        return synthetic;
    }

    private static String findInputParameterNameContainingVarRef(Element serviceTask, String varName) {
        String needle = "${" + varName + "}";
        NodeList byNs = serviceTask.getElementsByTagNameNS(CAMUNDA_NS, "inputParameter");
        for (int i = 0; i < byNs.getLength(); i++) {
            if (byNs.item(i) instanceof Element el) {
                if (StringUtils.contains(el.getTextContent(), needle)) {
                    String n = el.getAttribute("name");
                    if (StringUtils.isNotBlank(n)) {
                        return n.trim();
                    }
                }
            }
        }
        NodeList all = serviceTask.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element el)) {
                continue;
            }
            String ln = el.getLocalName();
            if (ln == null) {
                ln = stripPrefix(el.getTagName());
            }
            if ("inputParameter".equalsIgnoreCase(ln) && StringUtils.contains(el.getTextContent(), needle)) {
                String n = el.getAttribute("name");
                if (StringUtils.isNotBlank(n)) {
                    return n.trim();
                }
            }
        }
        return null;
    }

    private static BpmComponentParameter copyBpmParameter(BpmComponentParameter p) {
        BpmComponentParameter c = new BpmComponentParameter();
        BeanUtils.copyProperties(p, c);
        return c;
    }

    /**
     * 将组件任务 id 按 BPMN sequenceFlow 的拓扑序排列（Kahn）；无法纳入排序的孤立任务按 id 排在后面。
     */
    private static List<String> orderServiceTasksByFlow(Map<String, List<String>> forwardAdj, Set<String> serviceTaskIds) {
        if (serviceTaskIds.isEmpty()) {
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
            if (serviceTaskIds.contains(id)) {
                out.add(id);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String id : serviceTaskIds) {
            if (!out.contains(id)) {
                rest.add(id);
            }
        }
        Collections.sort(rest);
        out.addAll(rest);
        return out;
    }

    private static Set<String> collectOutputKeys(BpmComponent c) {
        Set<String> keys = new HashSet<>();
        if (c.getOutputParameters() == null) {
            return keys;
        }
        for (BpmComponentParameter p : c.getOutputParameters()) {
            if (p == null || p.isHidden()) {
                continue;
            }
            if (StringUtils.isNotBlank(p.getKey())) {
                keys.add(p.getKey().trim());
            }
        }
        return keys;
    }

    /**
     * targetRef -&gt; 若干 sourceRef，用于从某节点反向找所有可达前驱。
     */
    private static Map<String, List<String>> buildReverseAdjacency(Document doc) {
        Map<String, List<String>> rev = new HashMap<>();
        NodeList flows = doc.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            if (!(flows.item(i) instanceof Element flow)) {
                continue;
            }
            String src = flow.getAttribute("sourceRef");
            String tgt = flow.getAttribute("targetRef");
            if (StringUtils.isAnyBlank(src, tgt)) {
                continue;
            }
            rev.computeIfAbsent(tgt.trim(), k -> new ArrayList<>()).add(src.trim());
        }
        if (rev.isEmpty()) {
            appendSequenceFlowsByLocalName(doc.getDocumentElement(), rev);
        }
        return rev;
    }

    /** sourceRef -&gt; 若干 targetRef */
    private static Map<String, List<String>> buildForwardAdjacency(Document doc) {
        Map<String, List<String>> fwd = new HashMap<>();
        NodeList flows = doc.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            if (!(flows.item(i) instanceof Element flow)) {
                continue;
            }
            String src = flow.getAttribute("sourceRef");
            String tgt = flow.getAttribute("targetRef");
            if (StringUtils.isAnyBlank(src, tgt)) {
                continue;
            }
            fwd.computeIfAbsent(src.trim(), k -> new ArrayList<>()).add(tgt.trim());
        }
        if (fwd.isEmpty()) {
            appendSequenceFlowsForwardByLocalName(doc.getDocumentElement(), fwd);
        }
        return fwd;
    }

    private static void appendSequenceFlowsForwardByLocalName(Element root, Map<String, List<String>> fwd) {
        if (root == null) {
            return;
        }
        String ln = root.getLocalName();
        if (ln == null) {
            ln = stripPrefix(root.getTagName());
        }
        if ("sequenceFlow".equalsIgnoreCase(ln)) {
            String src = root.getAttribute("sourceRef");
            String tgt = root.getAttribute("targetRef");
            if (StringUtils.isNoneBlank(src, tgt)) {
                fwd.computeIfAbsent(src.trim(), k -> new ArrayList<>()).add(tgt.trim());
            }
        }
        NodeList ch = root.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            if (ch.item(i) instanceof Element child) {
                appendSequenceFlowsForwardByLocalName(child, fwd);
            }
        }
    }

    private static void appendSequenceFlowsByLocalName(Element root, Map<String, List<String>> rev) {
        if (root == null) {
            return;
        }
        String ln = root.getLocalName();
        if (ln == null) {
            ln = stripPrefix(root.getTagName());
        }
        if ("sequenceFlow".equalsIgnoreCase(ln)) {
            String src = root.getAttribute("sourceRef");
            String tgt = root.getAttribute("targetRef");
            if (StringUtils.isNoneBlank(src, tgt)) {
                rev.computeIfAbsent(tgt.trim(), k -> new ArrayList<>()).add(src.trim());
            }
        }
        NodeList ch = root.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            if (ch.item(i) instanceof Element child) {
                appendSequenceFlowsByLocalName(child, rev);
            }
        }
    }

    /** 在反向图上从 nodeId BFS，得到原图中所有能到达 nodeId 的节点 id（含网关、事件等） */
    private static Set<String> backwardReachable(Map<String, List<String>> reverseAdj, String nodeId) {
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

    private static String collectInputParameterText(Element serviceTask) {
        StringBuilder sb = new StringBuilder();
        NodeList byNs = serviceTask.getElementsByTagNameNS(CAMUNDA_NS, "inputParameter");
        appendElementTextContent(byNs, sb);
        if (sb.length() > 0) {
            return sb.toString();
        }
        NodeList all = serviceTask.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element el)) {
                continue;
            }
            String ln = el.getLocalName();
            if (ln == null) {
                ln = stripPrefix(el.getTagName());
            }
            if ("inputParameter".equalsIgnoreCase(ln)) {
                sb.append(el.getTextContent());
            }
        }
        return sb.toString();
    }

    private static void appendElementTextContent(NodeList nl, StringBuilder sb) {
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element el) {
                sb.append(el.getTextContent());
            }
        }
    }

    private static List<String> extractVariableRefs(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Matcher m = INPUT_VAR_REF.matcher(text);
        while (m.find()) {
            unique.add(m.group(1));
        }
        return new ArrayList<>(unique);
    }

    private static String stripPrefix(String tagName) {
        int i = tagName.indexOf(':');
        return i >= 0 ? tagName.substring(i + 1) : tagName;
    }

    private static String findCamundaPropertyValue(Element scope, String propertyName) {
        NodeList byNs = scope.getElementsByTagNameNS(CAMUNDA_NS, "property");
        String v = scanPropertyElements(byNs, propertyName);
        if (v != null) {
            return v;
        }
        NodeList all = scope.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element el)) {
                continue;
            }
            String ln = el.getLocalName();
            if (ln == null) {
                ln = stripPrefix(el.getTagName());
            }
            if (!"property".equalsIgnoreCase(ln)) {
                continue;
            }
            if (propertyName.equals(el.getAttribute("name"))) {
                return el.getAttribute("value");
            }
        }
        return null;
    }

    private static String scanPropertyElements(NodeList nl, String propertyName) {
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element el) {
                if (propertyName.equals(el.getAttribute("name"))) {
                    return el.getAttribute("value");
                }
            }
        }
        return null;
    }

    private static List<Element> findServiceTasks(Document doc) {
        List<Element> out = new ArrayList<>();
        NodeList ns = doc.getElementsByTagNameNS(BPMN_NS, "serviceTask");
        for (int i = 0; i < ns.getLength(); i++) {
            out.add((Element) ns.item(i));
        }
        if (!out.isEmpty()) {
            return out;
        }
        collectByLocalName(doc.getDocumentElement(), "serviceTask", out);
        return out;
    }

    private static void collectByLocalName(Element el, String wantLocal, List<Element> out) {
        String ln = el.getLocalName();
        if (ln == null) {
            ln = stripPrefix(el.getTagName());
        }
        if (wantLocal.equalsIgnoreCase(ln)) {
            out.add(el);
        }
        NodeList ch = el.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            Node n = ch.item(i);
            if (n instanceof Element child) {
                collectByLocalName(child, wantLocal, out);
            }
        }
    }
}
