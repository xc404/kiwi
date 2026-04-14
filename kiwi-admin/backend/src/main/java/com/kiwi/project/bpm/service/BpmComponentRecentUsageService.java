package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentPropertySnapshotEntry;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.model.BpmProcess;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 不落库：仅在查询时从当前用户已保存流程的 BPMN 中解析「最近使用的组件」，
 * 并将 {@link BpmComponentPropertySnapshotEntry} 合并进 {@link BpmComponent#getInputParameters()}/{@link BpmComponent#getOutputParameters()} 的 {@code defaultValue}。
 */
@Service
@RequiredArgsConstructor
public class BpmComponentRecentUsageService
{
    /**
     * 最近使用接口专用：在 {@link BpmComponent} 基础上增加来源流程时间。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RecentBpmComponent extends BpmComponent
    {
        private Date lastUsedFromProcessAt;
    }

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    /** 单次扫描流程数量上限，避免解析过多 XML */
    private static final int MAX_PROCESSES = 50;

    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmComponentService bpmComponentService;

    public List<RecentBpmComponent> listForCurrentUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            return List.of();
        }
        List<BpmProcess> processes = bpmProcessDefinitionDao.findByCreatedByOrderByUpdatedTimeDesc(
                userId, PageRequest.of(0, MAX_PROCESSES));
        LinkedHashMap<String, RecentBpmComponent> merged = new LinkedHashMap<>();
        for (BpmProcess p : processes) {
            if (p == null || StringUtils.isBlank(p.getBpmnXml())) {
                continue;
            }
            Date processTime = p.getUpdatedTime() != null ? p.getUpdatedTime() : p.getCreatedTime();
            Map<String, List<BpmComponentPropertySnapshotEntry>> inProcess = extractSnapshotsByComponentId(p.getBpmnXml());
            for (Map.Entry<String, List<BpmComponentPropertySnapshotEntry>> e : inProcess.entrySet()) {
                String cid = e.getKey();
                if (merged.containsKey(cid)) {
                    continue;
                }
                BpmComponent meta = bpmComponentService.resolveComponentById(cid);
                BpmComponent row;
                if (meta != null) {
                    BpmComponent detached = new BpmComponent();
                    BeanUtils.copyProperties(meta, detached);
                    row = bpmComponentService.fillComponentProperties(detached);
                } else {
                    row = new BpmComponent();
                    row.setId(cid);
                    row.setName(cid);
                }
                applySnapshotToParameterDefaults(row, e.getValue());
                RecentBpmComponent recent = new RecentBpmComponent();
                BeanUtils.copyProperties(row, recent);
                recent.setLastUsedFromProcessAt(processTime);
                merged.put(cid, recent);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 用 BPMN 解析条目覆盖参数表中同 key 项的 {@link BpmComponentParameter#getDefaultValue()}（存在则覆盖）。
     */
    static void applySnapshotToParameterDefaults(BpmComponent component, List<BpmComponentPropertySnapshotEntry> entries) {
        if (component == null || entries == null || entries.isEmpty()) {
            return;
        }
        for (BpmComponentPropertySnapshotEntry se : entries) {
            if (se == null || StringUtils.isBlank(se.getNamespace()) || StringUtils.isBlank(se.getKey())) {
                continue;
            }
            String v = se.getValue() != null ? se.getValue() : "";
            switch (se.getNamespace()) {
                case "inputParameter" -> applyDefaultToParamList(component.getInputParameters(), se.getKey(), v);
                case "outputParameter" -> applyDefaultToParamList(component.getOutputParameters(), se.getKey(), v);
                case "In" -> applyDefaultToParamList(component.getInputParameters(), se.getKey(), v);
                case "Out" -> applyDefaultToParamList(component.getOutputParameters(), se.getKey(), v);
                default -> {
                    /* taskAttr 等不写入参数表 */
                }
            }
        }
    }

    private static void applyDefaultToParamList(List<BpmComponentParameter> list, String key, String value) {
        if (list == null) {
            return;
        }
        for (BpmComponentParameter p : list) {
            if (p != null && key.equals(p.getKey())) {
                p.setDefaultValue(value);
                break;
            }
        }
    }

    /**
     * 单 BPMN 文档内：按前序遍历任务顺序，同一 componentId 后者覆盖前者。
     */
    Map<String, List<BpmComponentPropertySnapshotEntry>> extractSnapshotsByComponentId(String bpmnXml) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Map.of();
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return Map.of();
        }
        List<Element> tasks = new ArrayList<>();
        collectTasksPreorder(root, tasks);
        if (tasks.isEmpty()) {
            appendByLocalNameNs(doc, tasks, "serviceTask");
            appendByLocalNameNs(doc, tasks, "callActivity");
        }
        Map<String, List<BpmComponentPropertySnapshotEntry>> last = new LinkedHashMap<>();
        for (Element task : tasks) {
            String cid = findComponentId(task);
            if (StringUtils.isBlank(cid)) {
                continue;
            }
            last.put(cid.trim(), extractSnapshot(task));
        }
        return last;
    }

    private static void appendByLocalNameNs(Document doc, List<Element> out, String local) {
        NodeList ns = doc.getElementsByTagNameNS(BPMN_NS, local);
        for (int i = 0; i < ns.getLength(); i++) {
            if (ns.item(i) instanceof Element el) {
                out.add(el);
            }
        }
    }

    private static void collectTasksPreorder(Element el, List<Element> out) {
        String ln = el.getLocalName();
        if (ln == null) {
            ln = stripPrefix(el.getTagName());
        }
        String uri = el.getNamespaceURI();
        boolean bpmnLike = uri == null || uri.isEmpty() || BPMN_NS.equals(uri);
        if (bpmnLike && ("serviceTask".equalsIgnoreCase(ln) || "callActivity".equalsIgnoreCase(ln))) {
            out.add(el);
        }
        NodeList ch = el.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            if (ch.item(i) instanceof Element child) {
                collectTasksPreorder(child, out);
            }
        }
    }

    private static String stripPrefix(String tagName) {
        int i = tagName.indexOf(':');
        return i >= 0 ? tagName.substring(i + 1) : tagName;
    }

    private static String findComponentId(Element scope) {
        String v = findCamundaPropertyValue(scope, "componentId");
        if (StringUtils.isNotBlank(v)) {
            return v.trim();
        }
        return null;
    }

    private static List<BpmComponentPropertySnapshotEntry> extractSnapshot(Element task) {
        List<BpmComponentPropertySnapshotEntry> entries = new ArrayList<>();
        String ln = task.getLocalName();
        if (ln == null) {
            ln = stripPrefix(task.getTagName());
        }
        boolean isCall = "callActivity".equalsIgnoreCase(ln);
        boolean isService = "serviceTask".equalsIgnoreCase(ln);

        putCamundaTaskAttributes(entries, task);

        if (isService) {
            NodeList inParams = task.getElementsByTagNameNS(CAMUNDA_NS, "inputParameter");
            appendIoParams(entries, inParams, "inputParameter");
            if (entries.stream().noneMatch(e -> "inputParameter".equals(e.getNamespace()))) {
                NodeList all = task.getElementsByTagName("*");
                for (int i = 0; i < all.getLength(); i++) {
                    if (!(all.item(i) instanceof Element el)) {
                        continue;
                    }
                    String l = el.getLocalName();
                    if (l == null) {
                        l = stripPrefix(el.getTagName());
                    }
                    if ("inputParameter".equalsIgnoreCase(l)) {
                        String name = el.getAttribute("name");
                        if (StringUtils.isNotBlank(name)) {
                            entries.add(new BpmComponentPropertySnapshotEntry("inputParameter", name.trim(), textOrEmpty(el)));
                        }
                    }
                }
            }
            NodeList outParams = task.getElementsByTagNameNS(CAMUNDA_NS, "outputParameter");
            appendIoParams(entries, outParams, "outputParameter");
            if (entries.stream().noneMatch(e -> "outputParameter".equals(e.getNamespace()))) {
                NodeList all = task.getElementsByTagName("*");
                for (int i = 0; i < all.getLength(); i++) {
                    if (!(all.item(i) instanceof Element el)) {
                        continue;
                    }
                    String l = el.getLocalName();
                    if (l == null) {
                        l = stripPrefix(el.getTagName());
                    }
                    if ("outputParameter".equalsIgnoreCase(l)) {
                        String name = el.getAttribute("name");
                        if (StringUtils.isNotBlank(name)) {
                            entries.add(new BpmComponentPropertySnapshotEntry("outputParameter", name.trim(), textOrEmpty(el)));
                        }
                    }
                }
            }
        }

        if (isCall) {
            NodeList ins = task.getElementsByTagNameNS(CAMUNDA_NS, "in");
            for (int i = 0; i < ins.getLength(); i++) {
                if (!(ins.item(i) instanceof Element inEl)) {
                    continue;
                }
                String target = inEl.getAttribute("target");
                if (StringUtils.isBlank(target)) {
                    continue;
                }
                String src = inEl.getAttribute("source");
                String srcEx = inEl.getAttribute("sourceExpression");
                String val = StringUtils.isNotBlank(src) ? src : srcEx;
                entries.add(new BpmComponentPropertySnapshotEntry("In", target.trim(), val != null ? val : ""));
            }
            if (entries.stream().noneMatch(e -> "In".equals(e.getNamespace()))) {
                NodeList all = task.getElementsByTagName("*");
                for (int i = 0; i < all.getLength(); i++) {
                    if (!(all.item(i) instanceof Element inEl)) {
                        continue;
                    }
                    String l = inEl.getLocalName();
                    if (l == null) {
                        l = stripPrefix(inEl.getTagName());
                    }
                    if (!"in".equalsIgnoreCase(l)) {
                        continue;
                    }
                    String target = inEl.getAttribute("target");
                    if (StringUtils.isBlank(target)) {
                        continue;
                    }
                    String src = inEl.getAttribute("source");
                    String srcEx = inEl.getAttribute("sourceExpression");
                    String val = StringUtils.isNotBlank(src) ? src : srcEx;
                    entries.add(new BpmComponentPropertySnapshotEntry("In", target.trim(), val != null ? val : ""));
                }
            }

            NodeList outs = task.getElementsByTagNameNS(CAMUNDA_NS, "out");
            for (int i = 0; i < outs.getLength(); i++) {
                if (!(outs.item(i) instanceof Element outEl)) {
                    continue;
                }
                String source = outEl.getAttribute("source");
                String tgt = outEl.getAttribute("target");
                if (StringUtils.isBlank(source)) {
                    continue;
                }
                entries.add(new BpmComponentPropertySnapshotEntry("Out", source.trim(), tgt != null ? tgt : ""));
            }
        }

        return entries;
    }

    private static void putCamundaTaskAttributes(List<BpmComponentPropertySnapshotEntry> entries, Element task) {
        putAttr(entries, task, "delegateExpression");
        putAttr(entries, task, "type");
        putAttr(entries, task, "topic");
    }

    private static void putAttr(List<BpmComponentPropertySnapshotEntry> entries, Element task, String local) {
        String v = task.getAttributeNS(CAMUNDA_NS, local);
        if (StringUtils.isBlank(v)) {
            v = task.getAttribute("camunda:" + local);
        }
        if (StringUtils.isNotBlank(v)) {
            entries.add(new BpmComponentPropertySnapshotEntry("taskAttr", local, v));
        }
    }

    private static void appendIoParams(List<BpmComponentPropertySnapshotEntry> entries, NodeList nl, String ns) {
        for (int i = 0; i < nl.getLength(); i++) {
            if (!(nl.item(i) instanceof Element el)) {
                continue;
            }
            String name = el.getAttribute("name");
            if (StringUtils.isBlank(name)) {
                continue;
            }
            entries.add(new BpmComponentPropertySnapshotEntry(ns, name.trim(), textOrEmpty(el)));
        }
    }

    private static String textOrEmpty(Element el) {
        String t = el.getTextContent();
        return t != null ? t : "";
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
}
