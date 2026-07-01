package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.model.BpmTemplatePackManifest;
import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class BpmTemplatePackManifestScanner {

    private static final Pattern ComponentIdPattern = Pattern.compile(
            "componentId[\"'\\s=>:]+([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    private final BpmDesignerXmlValidator xmlValidator;

    public BpmTemplatePackManifest scan(List<String> bpmnXmlList, List<String> processKeys, List<String> entryKeys) {
        BpmTemplatePackManifest manifest = new BpmTemplatePackManifest();
        manifest.setKind(processKeys.size() <= 1 ? "single" : "solution");
        manifest.setProcessKeys(new ArrayList<>(processKeys));
        manifest.setEntryProcessKeys(entryKeys != null ? new ArrayList<>(entryKeys) : List.of());
        Set<String> components = new LinkedHashSet<>();
        List<BpmTemplatePackManifest.CallActivityBinding> bindings = new ArrayList<>();
        for (int i = 0; i < bpmnXmlList.size(); i++) {
            String xml = bpmnXmlList.get(i);
            if (StringUtils.isBlank(xml)) {
                continue;
            }
            xmlValidator.validate(xml);
            components.addAll(extractComponentIds(xml));
            String callerKey = i < processKeys.size() ? processKeys.get(i) : "p" + i;
            bindings.addAll(extractCallActivityBindings(xml, callerKey));
        }
        manifest.setRequiredComponentKeys(new ArrayList<>(components));
        manifest.setCallActivityBindings(bindings);
        return manifest;
    }

    public Set<String> extractComponentIds(String xml) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = ComponentIdPattern.matcher(xml);
        while (m.find()) {
            String id = m.group(1);
            if (StringUtils.isNotBlank(id)) {
                out.add(id.trim());
            }
        }
        return out;
    }

    public List<BpmTemplatePackManifest.CallActivityBinding> extractCallActivityBindings(String xml, String callerProcessKey) {
        List<BpmTemplatePackManifest.CallActivityBinding> out = new ArrayList<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList callActivities = doc.getElementsByTagNameNS("*", "callActivity");
            for (int i = 0; i < callActivities.getLength(); i++) {
                Element el = (Element) callActivities.item(i);
                String called = el.getAttribute("calledElement");
                if (StringUtils.isBlank(called)) {
                    called = el.getAttributeNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "calledElement");
                }
                if (StringUtils.isNotBlank(called)) {
                    BpmTemplatePackManifest.CallActivityBinding b = new BpmTemplatePackManifest.CallActivityBinding();
                    b.setCallerProcessKey(callerProcessKey);
                    b.setActivityId(el.getAttribute("id"));
                    b.setCalleeProcessKey(called.trim());
                    out.add(b);
                }
            }
        } catch (Exception ignored) {
            // 非阻塞：无法解析时跳过 CallActivity 扫描
        }
        return out;
    }

    public String remapCalledElements(String xml, java.util.Map<String, String> processKeyToNewId) {
        if (StringUtils.isBlank(xml) || processKeyToNewId == null || processKeyToNewId.isEmpty()) {
            return xml;
        }
        String result = xml;
        for (var e : processKeyToNewId.entrySet()) {
            String oldKey = e.getKey();
            String newId = e.getValue();
            if (StringUtils.isBlank(oldKey) || StringUtils.isBlank(newId) || oldKey.equals(newId)) {
                continue;
            }
            result = result.replace("calledElement=\"" + oldKey + "\"", "calledElement=\"" + newId + "\"");
            result = result.replace("calledElement='" + oldKey + "'", "calledElement='" + newId + "'");
        }
        return result;
    }
}
