package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmTemplatePackManifestScanner;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BpmAiWorkflowValidator {

    public static final String CodeXmlMalformed = "XML_MALFORMED";
    public static final String CodeNotDefinitions = "NOT_DEFINITIONS";
    public static final String CodeUnknownComponent = "UNKNOWN_COMPONENT";
    public static final String CodePluginNotInstalled = "PLUGIN_NOT_INSTALLED";
    public static final String CodeMissingRequiredParam = "MISSING_REQUIRED_PARAM";
    public static final String CodeDanglingFlow = "DANGLING_FLOW";
    public static final String CodeNoStart = "NO_START_EVENT";

    private final BpmDesignerXmlValidator xmlValidator;
    private final BpmComponentService bpmComponentService;
    private final BpmComponentPluginLoader bpmComponentPluginLoader;
    private final BpmTemplatePackManifestScanner manifestScanner;
    private final AiChatProperties aiChatProperties;
    private final ObjectMapper objectMapper;

    public ValidationResult validate(String xml, AiAuthoringCatalog catalog) {
        List<AiAuthoringValidationIssue> issues = new ArrayList<>();
        if (StringUtils.isBlank(xml)) {
            issues.add(AiAuthoringValidationIssue.of(CodeXmlMalformed, "BPMN XML 为空", "REPAIR"));
            return result(issues, 0);
        }
        try {
            xmlValidator.validate(xml);
        } catch (IllegalArgumentException ex) {
            issues.add(AiAuthoringValidationIssue.of(CodeXmlMalformed, ex.getMessage(), "REPAIR"));
            return result(issues, 0);
        }

        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setNamespaceAware(true);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            issues.add(AiAuthoringValidationIssue.of(CodeXmlMalformed, e.getMessage(), "REPAIR"));
            return result(issues, 0);
        }

        validateStructure(doc, issues);
        validateComponents(xml, catalog, issues);
        return result(issues, repairRoundIgnored());
    }

    public String toDispatchCode(List<AiAuthoringValidationIssue> issues, int repairRound) {
        if (issues == null || issues.isEmpty()) {
            return AiAuthoringVariables.DispatchPass;
        }
        boolean hasInstall = issues.stream().anyMatch(i -> "INSTALL".equals(i.getSeverity()));
        boolean hasAsk = issues.stream().anyMatch(i -> "ASK".equals(i.getSeverity()));
        boolean hasRepair = issues.stream().anyMatch(i -> "REPAIR".equals(i.getSeverity()));
        if (hasInstall) {
            return AiAuthoringVariables.DispatchInstall;
        }
        if (hasAsk) {
            return AiAuthoringVariables.DispatchAsk;
        }
        int max = aiChatProperties.getWorkflowAuthoring().getMaxRepairRounds();
        if (hasRepair && repairRound < max) {
            return AiAuthoringVariables.DispatchRepair;
        }
        return AiAuthoringVariables.DispatchAsk;
    }

    public String issuesAsJson(List<AiAuthoringValidationIssue> issues) {
        try {
            return objectMapper.writeValueAsString(issues != null ? issues : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    private void validateStructure(Document doc, List<AiAuthoringValidationIssue> issues) {
        NodeList starts = doc.getElementsByTagNameNS("*", "startEvent");
        if (starts.getLength() == 0) {
            issues.add(AiAuthoringValidationIssue.of(CodeNoStart, "缺少 startEvent", "REPAIR"));
        }
        Map<String, Element> byId = indexById(doc);
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            String source = flow.getAttribute("sourceRef");
            String target = flow.getAttribute("targetRef");
            if (StringUtils.isBlank(source) || !byId.containsKey(source)
                    || StringUtils.isBlank(target) || !byId.containsKey(target)) {
                AiAuthoringValidationIssue issue = AiAuthoringValidationIssue.of(
                        CodeDanglingFlow,
                        "sequenceFlow 端点无效: " + flow.getAttribute("id"),
                        "REPAIR");
                issue.setElementId(flow.getAttribute("id"));
                issues.add(issue);
            }
        }
    }

    private void validateComponents(String xml, AiAuthoringCatalog catalog, List<AiAuthoringValidationIssue> issues) {
        Set<String> componentIds = manifestScanner.extractComponentIds(xml);
        Map<String, AiAuthoringCatalog.CatalogComponent> installable = new LinkedHashMap<>();
        if (catalog != null && catalog.getInstallable() != null) {
            for (AiAuthoringCatalog.CatalogComponent c : catalog.getInstallable()) {
                installable.put(c.getId(), c);
            }
        }
        Map<String, String> jarIndex = bpmComponentPluginLoader.buildPluginJarIndex();
        for (String componentId : componentIds) {
            BpmComponent resolved = bpmComponentService.resolveComponentById(componentId);
            if (resolved != null) {
                checkRequiredParams(xml, componentId, resolved, issues);
                continue;
            }
            AiAuthoringCatalog.CatalogComponent avail = installable.get(componentId);
            if (avail != null) {
                AiAuthoringValidationIssue issue = AiAuthoringValidationIssue.of(
                        CodePluginNotInstalled,
                        "组件未安装，可从市场/插件安装: " + componentId,
                        "INSTALL");
                issue.setComponentId(componentId);
                issue.setPluginHint(avail.getPluginHint());
                issues.add(issue);
                continue;
            }
            if (componentId.startsWith("plugin_") && !jarIndex.containsKey(componentId)) {
                AiAuthoringValidationIssue issue = AiAuthoringValidationIssue.of(
                        CodePluginNotInstalled,
                        "插件组件未安装: " + componentId,
                        "INSTALL");
                issue.setComponentId(componentId);
                issues.add(issue);
                continue;
            }
            AiAuthoringValidationIssue issue = AiAuthoringValidationIssue.of(
                    CodeUnknownComponent,
                    "未知 componentId: " + componentId,
                    "ASK");
            issue.setComponentId(componentId);
            issues.add(issue);
        }
    }

    private void checkRequiredParams(
            String xml, String componentId, BpmComponent component, List<AiAuthoringValidationIssue> issues) {
        if (component.getInputParameters() == null) {
            return;
        }
        for (BpmComponentParameter p : component.getInputParameters()) {
            if (p == null || !p.isRequired() || StringUtils.isBlank(p.getKey())) {
                continue;
            }
            // Heuristic: required key appears in XML near componentId
            if (!xml.contains(p.getKey())) {
                AiAuthoringValidationIssue issue = AiAuthoringValidationIssue.of(
                        CodeMissingRequiredParam,
                        "组件 " + componentId + " 缺少必填参数 " + p.getKey(),
                        "ASK");
                issue.setComponentId(componentId);
                issues.add(issue);
            }
        }
    }

    private Map<String, Element> indexById(Document doc) {
        Map<String, Element> map = new LinkedHashMap<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element el) {
                String id = el.getAttribute("id");
                if (StringUtils.isNotBlank(id)) {
                    map.put(id, el);
                }
            }
        }
        return map;
    }

    private ValidationResult result(List<AiAuthoringValidationIssue> issues, int repairRound) {
        ValidationResult r = new ValidationResult();
        r.setIssues(issues);
        r.setDispatchCode(toDispatchCode(issues, repairRound));
        return r;
    }

    private int repairRoundIgnored() {
        return 0;
    }

    @lombok.Data
    public static class ValidationResult {
        private List<AiAuthoringValidationIssue> issues = new ArrayList<>();
        private String dispatchCode = AiAuthoringVariables.DispatchPass;
    }
}
