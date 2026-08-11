package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import com.kiwi.bpmn.assistant.spi.AssistantXmlValidator;
import lombok.RequiredArgsConstructor;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BPMN 校验：已装 / 可装 / 未知三态，不依赖本轮 Catalog 菜单。
 */
@Component
@RequiredArgsConstructor
public class AssistantWorkflowValidator {

    public static final String CodeXmlMalformed = "XML_MALFORMED";
    public static final String CodeNotDefinitions = "NOT_DEFINITIONS";
    public static final String CodeUnknownComponent = "UNKNOWN_COMPONENT";
    public static final String CodePluginNotInstalled = "PLUGIN_NOT_INSTALLED";
    public static final String CodeMissingRequiredParam = "MISSING_REQUIRED_PARAM";
    /** @deprecated 保留码值兼容旧评测；新逻辑不再因「不在 Catalog」报错。 */
    @Deprecated
    public static final String CodeComponentNotInCatalog = "COMPONENT_NOT_IN_CATALOG";
    public static final String CodeDanglingFlow = "DANGLING_FLOW";
    public static final String CodeNoStart = "NO_START_EVENT";
    public static final String CodeNoEnd = "NO_END_EVENT";
    public static final String CodeComponentIdChanged = "COMPONENT_ID_CHANGED";

    private final AssistantXmlValidator xmlValidator;
    private final AssistantComponentLookup componentLookup;
    private final AssistantProperties assistantProperties;
    private final ObjectMapper objectMapper;
    private final AssistantRuleSet ruleSet;

    public ValidationResult validate(String xml) {
        return validate(xml, null);
    }

    public ValidationResult validate(String xml, AssistantCatalog catalog) {
        List<AssistantValidationIssue> issues = new ArrayList<>();
        if (StringUtils.isBlank(xml)) {
            issues.add(AssistantValidationIssue.of(CodeXmlMalformed, "BPMN XML 为空", "REPAIR"));
            return result(issues, 0);
        }
        try {
            xmlValidator.validate(xml);
        } catch (IllegalArgumentException ex) {
            issues.add(AssistantValidationIssue.of(CodeXmlMalformed, ex.getMessage(), "REPAIR"));
            return result(issues, 0);
        }

        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setNamespaceAware(true);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            issues.add(AssistantValidationIssue.of(CodeXmlMalformed, e.getMessage(), "REPAIR"));
            return result(issues, 0);
        }

        validateStructure(doc, issues);
        validateComponents(doc, catalog, issues);
        return result(issues, repairRoundIgnored());
    }

    /**
     * modify 防误换：同一节点 id 的 componentId 被无故更换时记 ASK。
     */
    public void appendUnauthorizedComponentSwaps(
            String baseXml,
            String candidateXml,
            boolean userAllowedReplace,
            List<AssistantValidationIssue> issues) {
        if (userAllowedReplace || StringUtils.isBlank(baseXml) || StringUtils.isBlank(candidateXml)) {
            return;
        }
        Map<String, String> base = serviceTaskComponentIds(baseXml);
        Map<String, String> candidate = serviceTaskComponentIds(candidateXml);
        for (Map.Entry<String, String> entry : base.entrySet()) {
            String nodeId = entry.getKey();
            String baseCid = entry.getValue();
            if (!candidate.containsKey(nodeId)) {
                continue;
            }
            String nextCid = candidate.get(nodeId);
            if (AssistantComponentIdAliases.sameComponent(baseCid, nextCid)) {
                continue;
            }
            AssistantValidationIssue issue = AssistantValidationIssue.of(
                    CodeComponentIdChanged,
                    "节点 " + nodeId + " 的组件从 " + baseCid + " 被改为 " + nextCid
                            + "。整理/规范流程时不得擅自更换业务组件；若确需替换请明确说明。",
                    "ASK");
            issue.setRuleId(AssistantRuleSet.RuleModifyPreserveComponentIds);
            issue.setElementId(nodeId);
            issue.setComponentId(nextCid);
            issues.add(issue);
        }
    }

    public String toDispatchCode(List<AssistantValidationIssue> issues, int repairRound) {
        if (issues == null || issues.isEmpty()) {
            return AssistantVariables.DispatchPass;
        }
        boolean hasInstall = issues.stream().anyMatch(i -> "INSTALL".equals(i.getSeverity()));
        boolean hasAsk = issues.stream().anyMatch(i -> "ASK".equals(i.getSeverity()));
        boolean hasRepair = issues.stream().anyMatch(i -> "REPAIR".equals(i.getSeverity()));
        if (hasInstall) {
            return AssistantVariables.DispatchInstall;
        }
        if (hasAsk) {
            return AssistantVariables.DispatchAsk;
        }
        int max = assistantProperties.getMaxRepairRounds();
        if (hasRepair && repairRound < max) {
            return AssistantVariables.DispatchRepair;
        }
        return AssistantVariables.DispatchAsk;
    }

    public String issuesAsJson(List<AssistantValidationIssue> issues) {
        try {
            return objectMapper.writeValueAsString(issues != null ? issues : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    private void validateStructure(Document doc, List<AssistantValidationIssue> issues) {
        NodeList starts = doc.getElementsByTagNameNS("*", "startEvent");
        if (starts.getLength() == 0) {
            addRuleIssue(issues, AssistantRuleSet.RuleHasStartAndEnd,
                    CodeNoStart, "缺少 startEvent", "REPAIR");
        }
        NodeList ends = doc.getElementsByTagNameNS("*", "endEvent");
        if (ends.getLength() == 0) {
            addRuleIssue(issues, AssistantRuleSet.RuleHasStartAndEnd,
                    CodeNoEnd, "缺少 endEvent", "REPAIR");
        }
        Map<String, Element> byId = indexById(doc);
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            String source = flow.getAttribute("sourceRef");
            String target = flow.getAttribute("targetRef");
            if (StringUtils.isBlank(source) || !byId.containsKey(source)
                    || StringUtils.isBlank(target) || !byId.containsKey(target)) {
                AssistantValidationIssue issue = addRuleIssue(
                        issues,
                        AssistantRuleSet.RuleSequenceFlowEndpoints,
                        CodeDanglingFlow,
                        "sequenceFlow 端点无效: " + flow.getAttribute("id"),
                        "REPAIR");
                if (issue != null) {
                    issue.setElementId(flow.getAttribute("id"));
                }
            }
        }
    }

    private void validateComponents(Document doc, AssistantCatalog catalog, List<AssistantValidationIssue> issues) {
        Map<String, AssistantCatalog.CatalogComponent> installable = new LinkedHashMap<>();
        if (catalog != null && catalog.getInstallable() != null) {
            for (AssistantCatalog.CatalogComponent c : catalog.getInstallable()) {
                if (c != null && StringUtils.isNotBlank(c.getId())) {
                    installable.put(c.getId(), c);
                    String alt = AssistantComponentIdAliases.alternateId(c.getId());
                    if (alt != null) {
                        installable.putIfAbsent(alt, c);
                    }
                }
            }
        }
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element element)) {
                continue;
            }
            String componentId = componentId(element);
            if (StringUtils.isBlank(componentId)) {
                continue;
            }
            if (componentLookup.exists(componentId)) {
                checkRequiredParams(element, componentId, issues);
                continue;
            }
            var pluginHint = componentLookup.pluginMissingHint(componentId);
            if (pluginHint.isPresent()) {
                AssistantValidationIssue issue = AssistantValidationIssue.of(
                        CodePluginNotInstalled,
                        "组件未安装，可从市场/插件安装: " + componentId,
                        "INSTALL");
                setComponentContext(issue, element, componentId);
                issue.setPluginHint(pluginHint.get());
                issues.add(issue);
                continue;
            }
            AssistantCatalog.CatalogComponent avail = installable.get(componentId);
            if (avail != null) {
                AssistantValidationIssue issue = AssistantValidationIssue.of(
                        CodePluginNotInstalled,
                        "组件未安装，可从市场/插件安装: " + componentId,
                        "INSTALL");
                setComponentContext(issue, element, componentId);
                issue.setPluginHint(StringUtils.defaultIfBlank(avail.getPluginHint(), componentId));
                issues.add(issue);
                continue;
            }
            AssistantValidationIssue issue = addRuleIssue(
                    issues,
                    AssistantRuleSet.RuleComponentIdResolvable,
                    CodeUnknownComponent,
                    "未知 componentId: " + componentId,
                    "ASK");
            setComponentContext(issue, element, componentId);
        }
    }

    private void checkRequiredParams(
            Element componentElement,
            String componentId,
            List<AssistantValidationIssue> issues) {
        List<String> required = componentLookup.requiredInputKeys(componentId);
        if (required.isEmpty()) {
            return;
        }
        Set<String> inputNames = inputParameterNames(componentElement);
        for (String key : required) {
            if (StringUtils.isBlank(key) || inputNames.contains(key)) {
                continue;
            }
            AssistantValidationIssue issue = addRuleIssue(
                    issues,
                    AssistantRuleSet.RuleRequiredParamsPresent,
                    CodeMissingRequiredParam,
                    "组件 " + componentId + " 缺少必填参数 " + key,
                    "ASK");
            setComponentContext(issue, componentElement, componentId);
        }
    }

    private Map<String, String> serviceTaskComponentIds(String xml) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                if (!(all.item(i) instanceof Element el)) {
                    continue;
                }
                if (!"serviceTask".equals(el.getLocalName())) {
                    continue;
                }
                String id = el.getAttribute("id");
                String cid = componentId(el);
                if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(cid)) {
                    map.put(id, cid);
                }
            }
        } catch (Exception ignored) {
            // ignore parse errors here
        }
        return map;
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

    private Set<String> inputParameterNames(Element componentElement) {
        Set<String> names = new HashSet<>();
        NodeList descendants = componentElement.getElementsByTagNameNS("*", "inputParameter");
        for (int i = 0; i < descendants.getLength(); i++) {
            if (descendants.item(i) instanceof Element input) {
                String name = input.getAttribute("name");
                if (StringUtils.isNotBlank(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private AssistantValidationIssue addRuleIssue(
            List<AssistantValidationIssue> issues,
            String ruleId,
            String code,
            String message,
            String defaultSeverity) {
        AssistantRule rule = ruleSet.findHard(ruleId).orElse(null);
        if (rule == null || !rule.isEnabled()) {
            return null;
        }
        String severity = StringUtils.defaultIfBlank(rule.getSeverity(), defaultSeverity);
        AssistantValidationIssue issue = AssistantValidationIssue.of(code, message, severity);
        issue.setRuleId(ruleId);
        issues.add(issue);
        return issue;
    }

    private void setComponentContext(
            AssistantValidationIssue issue, Element element, String componentId) {
        if (issue == null) {
            return;
        }
        issue.setComponentId(componentId);
        issue.setElementId(element.getAttribute("id"));
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

    private ValidationResult result(List<AssistantValidationIssue> issues, int repairRound) {
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
        private List<AssistantValidationIssue> issues = new ArrayList<>();
        private String dispatchCode = AssistantVariables.DispatchPass;
    }
}
