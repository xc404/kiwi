package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AiChatProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan + BPMN 生成：优先 ChatClient；失败时按 Catalog 已装组件生成最小可解析流程。
 */
@Component
public class AiAuthoringPlanGenerateService {

    private final ObjectMapper objectMapper;
    private final AiChatProperties aiChatProperties;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final AiAuthoringRuleSet ruleSet;
    private final AiWorkflowPlanCompiler planCompiler;

    public AiAuthoringPlanGenerateService(
            ObjectMapper objectMapper,
            AiChatProperties aiChatProperties,
            @Qualifier("kiwiChatClient") ObjectProvider<ChatClient> chatClientProvider,
            AiAuthoringRuleSet ruleSet,
            AiWorkflowPlanCompiler planCompiler) {
        this.objectMapper = objectMapper;
        this.aiChatProperties = aiChatProperties;
        this.chatClientProvider = chatClientProvider;
        this.ruleSet = ruleSet;
        this.planCompiler = planCompiler;
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
        GenerateResult keepPrevious = hasBase ? keepPreviousXml(scenario, previousXml) : null;
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null || !aiChatProperties.isEnabled()) {
            return keepPrevious != null ? keepPrevious : scratchFallback;
        }
        try {
            String prompt = """
                    你是 Kiwi BPMN 设计助手。根据用户意图与 Catalog 设计工作流。
                    当前模式: %s
                    %s
                    Plan IR schema:
                    {"processId":"合法 XML id","name":"流程名",
                     "nodes":[{"id":"节点id","type":"startEvent|endEvent|serviceTask|userTask|exclusiveGateway",
                               "name":"节点名","componentId":"serviceTask 使用 Catalog.installed.id","parameters":{"key":"value"}}],
                     "flows":[{"id":"连线id","sourceRef":"源节点id","targetRef":"目标节点id","condition":"可空表达式"}]}
                    create 模式必须提供完整 Plan IR，由服务端确定性编译 XML；modify 模式同时提供修改后的完整 candidateXml。
                    用户意图/场景:
                    %s
                    Catalog:
                    %s
                    当前/上一版 XML（可为空；非空则优先修改它）:
                    %s
                    校验问题（可为空；修复时请针对 ruleId 与 message 逐项处理）:
                    %s
                    用户补充说明（可为空；非空时必须纳入本轮生成/修复）:
                    %s
                    """.formatted(
                    mode,
                    ruleSet.renderSoftPrompt(mode),
                    nullToEmpty(scenario),
                    nullToEmpty(catalogJson),
                    nullToEmpty(previousXml),
                    nullToEmpty(issuesJson),
                    nullToEmpty(userAnswer));
            String raw = client.prompt().user(prompt).call().content();
            if (StringUtils.isBlank(raw)) {
                return keepPrevious != null ? keepPrevious : scratchFallback;
            }
            String json = stripFence(raw);
            JsonNode node = objectMapper.readTree(json);
            GenerateResult r = new GenerateResult();
            r.setPlanIrJson(jsonOr(node, "planIrJson",
                    keepPrevious != null ? keepPrevious.getPlanIrJson() : scratchFallback.getPlanIrJson()));
            String xml = textOr(node, "candidateXml", null);
            Optional<String> compiled = hasBase
                    ? Optional.empty()
                    : planCompiler.compile(r.getPlanIrJson(), catalogJson);
            if (compiled.isPresent()) {
                r.setCandidateXml(compiled.orElseThrow());
            } else if (StringUtils.isNotBlank(xml)) {
                r.setCandidateXml(xml);
            } else if (keepPrevious != null) {
                r.setCandidateXml(keepPrevious.getCandidateXml());
            } else {
                r.setCandidateXml(scratchFallback.getCandidateXml());
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

    private GenerateResult keepPreviousXml(String scenario, String previousXml) {
        GenerateResult r = new GenerateResult();
        try {
            r.setPlanIrJson(objectMapper.writeValueAsString(
                    java.util.Map.of("intent", "keep_previous", "scenario", nullToEmpty(scenario))));
        } catch (Exception e) {
            r.setPlanIrJson("{\"intent\":\"keep_previous\"}");
        }
        r.setCandidateXml(previousXml);
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
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="http://kiwi.io/schema/bpmn"
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
        GenerateResult r = new GenerateResult();
        try {
            r.setPlanIrJson(objectMapper.writeValueAsString(
                    java.util.Map.of("intent", "create_from_scenario", "components", componentIds)));
        } catch (Exception e) {
            r.setPlanIrJson("{\"intent\":\"create_from_scenario\"}");
        }
        r.setCandidateXml(xml);
        r.setAssistantReply(buildFallbackReply(scenario, componentIds));
        return r;
    }

    private static String buildFallbackReply(String scenario, List<String> componentIds) {
        String names = componentIds.stream().map(AiAuthoringPlanGenerateService::shortName).reduce((a, b) -> a + "、" + b)
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
