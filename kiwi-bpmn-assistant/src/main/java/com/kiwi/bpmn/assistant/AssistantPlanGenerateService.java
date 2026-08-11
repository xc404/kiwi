package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Plan IR 生成：经 MCP 发现组件/模板；始终经 {@link AssistantPlanCompiler} 编译 XML。
 * 不再向 prompt 注入 Catalog 菜单。
 */
@Component
public class AssistantPlanGenerateService {

    static final Set<String> DiscoveryToolNames = Set.of(
            "bpmComp_aiPage",
            "bpmComp_listGrouped",
            "bpmRemoteMarket_list",
            "bpmRemoteMarket_get",
            "bpmMarket_aiPage",
            "bpmMarket_get",
            "bpmMarket_getProcess");

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

    /** @deprecated catalogJson 已忽略，请用无 catalog 的四参数重载 */
    @Deprecated
    public GenerateResult generate(
            String scenario,
            String catalogJson,
            String issuesJson,
            String previousXml,
            String userAnswer) {
        return generate(scenario, issuesJson, previousXml, userAnswer);
    }

    public GenerateResult generate(
            String scenario,
            String issuesJson,
            String previousXml,
            String userAnswer) {
        boolean hasBase = StringUtils.isNotBlank(previousXml);
        String mode = ruleSet.resolveMode(previousXml);
        GenerateResult scratchFallback = fallbackFromScenario(scenario);
        GenerateResult keepPrevious = hasBase ? keepPreviousXml(scenario, previousXml) : null;
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
                    你是 Kiwi BPMN 设计助手。根据用户意图设计工作流。
                    当前模式: %s
                    %s
                    组件与模板发现（必须使用工具，禁止臆造 componentId）：
                    - 已装组件：bpmComp_aiPage(keyword,page,size)、bpmComp_listGrouped
                    - 市场插件：bpmRemoteMarket_list(type=plugin)、bpmRemoteMarket_get
                    - 市场/本地模板：bpmRemoteMarket_list(type=template)、bpmMarket_aiPage、bpmMarket_get、bpmMarket_getProcess
                    不要调用 assistant_designer_* 或写库类工具；只输出 JSON。
                    Plan IR schema:
                    {"processId":"合法 XML id","name":"流程名",
                     "nodes":[{"id":"节点id","type":"startEvent|endEvent|serviceTask|userTask|exclusiveGateway",
                               "name":"节点名","componentId":"serviceTask 使用工具查到的 id","parameters":{"key":"value"}}],
                     "flows":[{"id":"连线id","sourceRef":"源节点id","targetRef":"目标节点id","condition":"可空表达式"}]}
                    create 与 modify 均只输出 planIrJson（完整 Plan IR）；禁止输出 candidateXml。服务端将确定性编译 XML。
                    若工具显示组件需安装，仍可写入该 componentId，服务端会提醒用户安装。
                    用户意图/场景:
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
                    nullToEmpty(basePlanIrJson),
                    nullToEmpty(previousXml),
                    issuesJsonForLlm(issuesJson),
                    nullToEmpty(userAnswer));
            String raw = client.prompt()
                    .user(prompt)
                    .options(ToolCallingChatOptions.builder()
                            .toolNames(DiscoveryToolNames))
                    .call()
                    .content();
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
            Optional<String> compiled = planCompiler.compile(planIr);
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

    private GenerateResult keepPreviousXml(String scenario, String previousXml) {
        GenerateResult r = new GenerateResult();
        Optional<AssistantPlan> parsed = bpmnToPlan.parse(previousXml);
        if (parsed.isPresent()) {
            try {
                String planIr = objectMapper.writeValueAsString(parsed.get());
                r.setPlanIrJson(planIr);
                Optional<String> compiled = planCompiler.compile(planIr);
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

    private GenerateResult fallbackFromScenario(String scenario) {
        String processId = "ai_gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = StringUtils.defaultIfBlank(scenario, "AI Generated");
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        AssistantPlan plan = new AssistantPlan();
        plan.setProcessId(processId);
        plan.setName(name);
        plan.getNodes().add(simpleNode("StartEvent_1", "startEvent", "开始", null));
        plan.getNodes().add(simpleNode("EndEvent_1", "endEvent", "结束", null));
        AssistantPlan.Flow endFlow = new AssistantPlan.Flow();
        endFlow.setId("Flow_end");
        endFlow.setSourceRef("StartEvent_1");
        endFlow.setTargetRef("EndEvent_1");
        plan.getFlows().add(endFlow);

        GenerateResult r = new GenerateResult();
        try {
            String planIr = objectMapper.writeValueAsString(plan);
            r.setPlanIrJson(planIr);
            Optional<String> compiled = planCompiler.compile(planIr);
            r.setCandidateXml(compiled.orElseGet(() -> minimalRawXml(processId, scenario)));
        } catch (Exception e) {
            r.setPlanIrJson("{\"intent\":\"create_from_scenario\"}");
            r.setCandidateXml(minimalRawXml(processId, scenario));
        }
        r.setAssistantReply(buildFallbackReply(scenario));
        return r;
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

    private String minimalRawXml(String processId, String scenario) {
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
                    <bpmn:endEvent id="EndEvent_1" name="结束"/>
                    <bpmn:sequenceFlow id="Flow_end" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(processId, processId, escape(scenario));
    }

    private static String buildFallbackReply(String scenario) {
        String scene = StringUtils.isBlank(scenario) ? "你的需求" : scenario.trim();
        if (scene.length() > 80) {
            scene = scene.substring(0, 80) + "…";
        }
        return "已根据「" + scene + "」生成候选骨架流程（开始→结束）。"
                + "请补充具体业务步骤，或确认后由助手通过组件查询继续完善。";
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
