package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan IR 生成：优先 ChatClient；始终经 {@link AssistantPlanCompiler} 编译 XML，忽略 LLM candidateXml。
 */
@Component
public class AssistantPlanGenerateService {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final AssistantRuleSet ruleSet;
    private final AssistantPlanCompiler planCompiler;
    private final AssistantBpmnToPlan bpmnToPlan;

    @Autowired
    public AssistantPlanGenerateService(
            ObjectMapper objectMapper,
            @Qualifier("kiwiChatClient") ObjectProvider<ChatClient> chatClientProvider,
            AssistantRuleSet ruleSet,
            AssistantPlanCompiler planCompiler,
            AssistantBpmnToPlan bpmnToPlan) {
        this.objectMapper = objectMapper;
        this.chatClientProvider = chatClientProvider;
        this.ruleSet = ruleSet;
        this.planCompiler = planCompiler;
        this.bpmnToPlan = bpmnToPlan;
    }

    /** 测试或无 Qualifier 场景 */
    public AssistantPlanGenerateService(
            ObjectMapper objectMapper,
            ObjectProvider<ChatClient> chatClientProvider,
            AssistantRuleSet ruleSet,
            AssistantPlanCompiler planCompiler,
            AssistantBpmnToPlan bpmnToPlan,
            boolean ignoreQualifier) {
        this.objectMapper = objectMapper;
        this.chatClientProvider = chatClientProvider;
        this.ruleSet = ruleSet;
        this.planCompiler = planCompiler;
        this.bpmnToPlan = bpmnToPlan;
    }

    public GenerateResult generate(String scenario, String catalogJson, String issuesJson, String previousXml) {
        return generate(scenario, catalogJson, issuesJson, previousXml, null);
    }

    public GenerateResult generate(
            String scenario,
            String catalogJson,
            String issuesJson,
            String previousXml,
            String userAnswer) {
        boolean hasBase = StringUtils.isNotBlank(previousXml);
        String mode = ruleSet.resolveMode(previousXml);
        GenerateResult scratchFallback = fallbackFromCatalog(scenario, catalogJson);
        GenerateResult keepPrevious = hasBase ? keepPreviousXml(scenario, previousXml, catalogJson) : null;
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return keepPrevious != null ? keepPrevious : scratchFallback;
        }
        try {
            String basePlanIrJson = "";
            if (hasBase) {
                Optional<AssistantPlan> basePlan = bpmnToPlan.parse(previousXml);
                if (basePlan.isPresent()) {
                    basePlanIrJson = objectMapper.writeValueAsString(basePlan.get());
                }
            }
            String prompt = """
                    你是 Kiwi BPMN 设计助手。根据用户意图与 Catalog 设计工作流。
                    当前模式: %s
                    %s
                    Plan IR schema:
                    {"processId":"合法 XML id","name":"流程名",
                     "nodes":[{"id":"节点id","type":"startEvent|endEvent|serviceTask|userTask|exclusiveGateway",
                               "name":"节点名","componentId":"serviceTask 使用 Catalog.components.id","parameters":{"key":"value"}}],
                     "flows":[{"id":"连线id","sourceRef":"源节点id","targetRef":"目标节点id","condition":"可空表达式"}]}
                    create 与 modify 均只输出 planIrJson（完整 Plan IR）；禁止输出 candidateXml。服务端将确定性编译 XML。
                    Catalog.components 为本轮全部可选组件；若其中有未安装项，服务端会提醒用户安装，你不要判断或提议安装。
                    用户意图/场景:
                    %s
                    Catalog:
                    %s
                    上一版 Plan IR（modify 时非空，请在此基础上修改）:
                    %s
                    当前/上一版 XML（参考；勿直接作为输出）:
                    %s
                    校验问题（可为空；修复时请针对 ruleId 与 message 逐项处理；不含 INSTALL）:
                    %s
                    用户补充说明（可为空；非空时必须纳入本轮生成/修复）:
                    %s
                    """.formatted(
                    mode,
                    ruleSet.renderSoftPrompt(mode),
                    nullToEmpty(scenario),
                    catalogJsonForLlm(catalogJson),
                    nullToEmpty(basePlanIrJson),
                    nullToEmpty(previousXml),
                    issuesJsonForLlm(issuesJson),
                    nullToEmpty(userAnswer));
            String raw = client.prompt().user(prompt).call().content();
            if (StringUtils.isBlank(raw)) {
                return keepPrevious != null ? keepPrevious : scratchFallback;
            }
            String json = stripFence(raw);
            JsonNode node = objectMapper.readTree(json);
            GenerateResult r = new GenerateResult();
            String planIr = jsonOr(node, "planIrJson", null);
            if (StringUtils.isBlank(planIr) && looksLikePlan(node)) {
                planIr = objectMapper.writeValueAsString(node);
            }
            if (StringUtils.isBlank(planIr)) {
                planIr = keepPrevious != null ? keepPrevious.getPlanIrJson() : scratchFallback.getPlanIrJson();
            }
            r.setPlanIrJson(planIr);
            Optional<String> compiled = planCompiler.compile(planIr, catalogJson);
            if (compiled.isPresent()) {
                r.setCandidateXml(compiled.get());
            } else if (keepPrevious != null) {
                r.setCandidateXml(keepPrevious.getCandidateXml());
                r.setPlanIrJson(keepPrevious.getPlanIrJson());
            } else {
                r.setCandidateXml(scratchFallback.getCandidateXml());
                r.setPlanIrJson(scratchFallback.getPlanIrJson());
            }
            String summary = textOr(node, "summary", null);
            if (StringUtils.isNotBlank(summary)) {
                r.setAssistantReply(summary.trim());
            } else if (keepPrevious != null) {
                r.setAssistantReply(keepPrevious.getAssistantReply());
            } else {
                r.setAssistantReply(scratchFallback.getAssistantReply());
            }
            return r;
        } catch (Exception e) {
            return keepPrevious != null ? keepPrevious : scratchFallback;
        }
    }

    private boolean looksLikePlan(JsonNode node) {
        return node != null && node.has("nodes") && node.has("flows");
    }

    /**
     * LLM 可见全部组件（installed ∪ installable），但不暴露 requiresInstall / 安装状态字段，
     * 避免模型自行判断是否安装；安装由校验阶段发现并提醒用户。
     */
    String catalogJsonForLlm(String catalogJson) {
        try {
            AssistantCatalog catalog = objectMapper.readValue(
                    StringUtils.defaultIfBlank(catalogJson, "{}"), AssistantCatalog.class);
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            appendComponentsForLlm(byId, catalog.getInstallable());
            appendComponentsForLlm(byId, catalog.getInstalled());
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("components", new ArrayList<>(byId.values()));
            if (catalog.getTemplates() != null && !catalog.getTemplates().isEmpty()) {
                slim.put("templates", catalog.getTemplates());
            }
            return objectMapper.writeValueAsString(slim);
        } catch (Exception e) {
            return StringUtils.defaultIfBlank(catalogJson, "{\"components\":[]}");
        }
    }

    private void appendComponentsForLlm(
            Map<String, Map<String, Object>> byId,
            List<AssistantCatalog.CatalogComponent> components) {
        if (components == null) {
            return;
        }
        for (AssistantCatalog.CatalogComponent c : components) {
            if (c == null || StringUtils.isBlank(c.getId())) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("id", c.getId());
            if (StringUtils.isNotBlank(c.getName())) {
                copy.put("name", c.getName());
            }
            if (StringUtils.isNotBlank(c.getDescription())) {
                copy.put("description", c.getDescription());
            }
            if (StringUtils.isNotBlank(c.getDelegateExpression())) {
                copy.put("delegateExpression", c.getDelegateExpression());
            }
            if (StringUtils.isNotBlank(c.getSource())) {
                copy.put("source", c.getSource());
            }
            if (StringUtils.isNotBlank(c.getGroup())) {
                copy.put("group", c.getGroup());
            }
            if (c.getInputs() != null && !c.getInputs().isEmpty()) {
                copy.put("inputs", c.getInputs());
            }
            byId.put(c.getId(), copy);
        }
    }

    /** INSTALL 类问题由流程网关处理，不交给 LLM。 */
    String issuesJsonForLlm(String issuesJson) {
        if (StringUtils.isBlank(issuesJson)) {
            return "";
        }
        try {
            List<AssistantValidationIssue> issues = objectMapper.readValue(
                    issuesJson,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, AssistantValidationIssue.class));
            List<AssistantValidationIssue> forLlm = new ArrayList<>();
            for (AssistantValidationIssue issue : issues) {
                if (issue == null) {
                    continue;
                }
                if ("INSTALL".equalsIgnoreCase(issue.getSeverity())) {
                    continue;
                }
                forLlm.add(issue);
            }
            return objectMapper.writeValueAsString(forLlm);
        } catch (Exception e) {
            return issuesJson;
        }
    }

    private GenerateResult keepPreviousXml(String scenario, String previousXml, String catalogJson) {
        GenerateResult r = new GenerateResult();
        Optional<AssistantPlan> parsed = bpmnToPlan.parse(previousXml);
        if (parsed.isPresent()) {
            try {
                String planIr = objectMapper.writeValueAsString(parsed.get());
                r.setPlanIrJson(planIr);
                Optional<String> compiled = planCompiler.compile(planIr, catalogJson);
                r.setCandidateXml(compiled.orElse(previousXml));
            } catch (Exception e) {
                r.setPlanIrJson("{\"intent\":\"keep_previous\"}");
                r.setCandidateXml(previousXml);
            }
        } else {
            try {
                r.setPlanIrJson(objectMapper.writeValueAsString(
                        Map.of("intent", "keep_previous", "scenario", nullToEmpty(scenario))));
            } catch (Exception e) {
                r.setPlanIrJson("{\"intent\":\"keep_previous\"}");
            }
            r.setCandidateXml(previousXml);
        }
        r.setAssistantReply("未能完成对本流程图的修改，已保留当前画布内容。请换种更具体的说法再试。");
        return r;
    }

    private GenerateResult fallbackFromCatalog(String scenario, String catalogJson) {
        List<String> componentIds = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(StringUtils.defaultIfBlank(catalogJson, "{}"));
            JsonNode installed = root.path("installed");
            if (installed.isArray()) {
                for (JsonNode n : installed) {
                    String id = n.path("id").asText(null);
                    if (StringUtils.isNotBlank(id)) {
                        componentIds.add(id);
                    }
                    if (componentIds.size() >= 3) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        if (componentIds.isEmpty()) {
            componentIds.add("classpath_httpRequest");
        }
        String processId = "ai_gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = StringUtils.defaultIfBlank(scenario, "AI Generated");
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId(processId);
        plan.setName(name);
        plan.getNodes().add(simpleNode("StartEvent_1", "startEvent", "开始", null));
        String prev = "StartEvent_1";
        int i = 1;
        for (String cid : componentIds) {
            String tid = "Activity_" + i;
            plan.getNodes().add(simpleNode(tid, "serviceTask", shortName(cid), cid));
            AssistantPlan.Flow flow = new AssistantPlan.Flow();
            flow.setId("Flow_" + i);
            flow.setSourceRef(prev);
            flow.setTargetRef(tid);
            plan.getFlows().add(flow);
            prev = tid;
            i++;
        }
        plan.getNodes().add(simpleNode("EndEvent_1", "endEvent", "结束", null));
        AssistantPlan.Flow endFlow = new AssistantPlan.Flow();
        endFlow.setId("Flow_end");
        endFlow.setSourceRef(prev);
        endFlow.setTargetRef("EndEvent_1");
        plan.getFlows().add(endFlow);

        GenerateResult r = new GenerateResult();
        String catalogForCompile = ensureCatalogHas(catalogJson, componentIds);
        try {
            String planIr = objectMapper.writeValueAsString(plan);
            r.setPlanIrJson(planIr);
            Optional<String> compiled = planCompiler.compile(planIr, catalogForCompile);
            r.setCandidateXml(compiled.orElseGet(() -> minimalRawXml(processId, scenario, componentIds)));
        } catch (Exception e) {
            r.setPlanIrJson("{\"intent\":\"create_from_scenario\"}");
            r.setCandidateXml(minimalRawXml(processId, scenario, componentIds));
        }
        r.setAssistantReply(buildFallbackReply(scenario, componentIds));
        return r;
    }

    private String ensureCatalogHas(String catalogJson, List<String> componentIds) {
        try {
            AssistantCatalog catalog = objectMapper.readValue(
                    StringUtils.defaultIfBlank(catalogJson, "{}"), AssistantCatalog.class);
            if (catalog.getInstalled() == null) {
                catalog.setInstalled(new ArrayList<>());
            }
            for (String id : componentIds) {
                boolean present = catalog.getInstalled().stream()
                        .anyMatch(c -> id.equals(c.getId()));
                if (!present) {
                    AssistantCatalog.CatalogComponent c = new AssistantCatalog.CatalogComponent();
                    c.setId(id);
                    c.setDelegateExpression("${" + beanFrom(id) + "}");
                    c.setStatus("installed");
                    catalog.getInstalled().add(c);
                }
            }
            return objectMapper.writeValueAsString(catalog);
        } catch (Exception e) {
            return catalogJson;
        }
    }

    private AssistantPlan.Node simpleNode(String id, String type, String name, String componentId) {
        AssistantPlan.Node node = new AssistantPlan.Node();
        node.setId(id);
        node.setType(type);
        node.setName(name);
        node.setComponentId(componentId);
        if (componentId != null) {
            node.setParameters(new LinkedHashMap<>());
        }
        return node;
    }

    private String minimalRawXml(String processId, String scenario, List<String> componentIds) {
        StringBuilder tasks = new StringBuilder();
        StringBuilder flows = new StringBuilder();
        String prev = "StartEvent_1";
        int i = 1;
        for (String cid : componentIds) {
            String tid = "Activity_" + i;
            tasks.append("""
                      <bpmn:serviceTask id="%s" name="%s" camunda:delegateExpression="${%s}" kiwi:componentId="%s"/>
                    """.formatted(tid, shortName(cid), beanFrom(cid), cid));
            flows.append("""
                      <bpmn:sequenceFlow id="Flow_%d" sourceRef="%s" targetRef="%s"/>
                    """.formatted(i, prev, tid));
            prev = tid;
            i++;
        }
        flows.append("""
                      <bpmn:sequenceFlow id="Flow_end" sourceRef="%s" targetRef="EndEvent_1"/>
                    """.formatted(prev));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="http://kiwi.com/bpmn"
                                  id="Definitions_%s"
                                  targetNamespace="http://kiwi.io/ai-authoring">
                  <bpmn:process id="%s" name="%s" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="开始"/>
                %s
                    <bpmn:endEvent id="EndEvent_1" name="结束"/>
                %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(processId, processId, escape(scenario), tasks, flows);
    }

    private static String buildFallbackReply(String scenario, List<String> componentIds) {
        String names = componentIds.stream().map(AssistantPlanGenerateService::shortName).reduce((a, b) -> a + "、" + b)
                .orElse("基础组件");
        String scene = StringUtils.isBlank(scenario) ? "你的需求" : scenario.trim();
        if (scene.length() > 80) {
            scene = scene.substring(0, 80) + "…";
        }
        return "已根据「" + scene + "」生成候选工作流，串联组件：" + names
                + "。请在右上角「AI 写工作流」面板预览并确认是否保存到当前流程。";
    }

    private static String beanFrom(String componentId) {
        if (componentId == null) {
            return "unknown";
        }
        int idx = componentId.indexOf('_');
        return idx >= 0 ? componentId.substring(idx + 1) : componentId;
    }

    private static String shortName(String componentId) {
        return beanFrom(componentId);
    }

    private static String escape(String s) {
        if (s == null) {
            return "AI Generated";
        }
        String t = s.length() > 40 ? s.substring(0, 40) : s;
        return t.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    private static String stripFence(String raw) {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (firstNl > 0 && last > firstNl) {
                return t.substring(firstNl + 1, last).trim();
            }
        }
        return t;
    }

    private static String textOr(JsonNode node, String field, String defaultValue) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || StringUtils.isBlank(n.asText())) {
            return defaultValue;
        }
        return n.asText();
    }

    private String jsonOr(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isTextual()) {
            return StringUtils.defaultIfBlank(value.asText(), defaultValue);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @lombok.Data
    public static class GenerateResult {
        private String planIrJson;
        private String candidateXml;
        /** 面向用户的自然语言说明 */
        private String assistantReply;
    }
}
