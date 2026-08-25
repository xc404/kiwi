package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolScope;
import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolTraceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 调用 LLM + MCP 生成 {@link EditPlan} 或只读解释。
 */
@Component
@Slf4j
public class DesignerAgentPlanGenerator {

    /** LLM 偶发输出 JS 风格 JSON（无引号键、单引号等）时的容错解析。 */
    private static final ObjectMapper LenientJsonMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatClient> chatClientProvider;

    @Autowired
    public DesignerAgentPlanGenerator(
            ObjectMapper objectMapper,
            @Qualifier("designerAgentChatClient") ObjectProvider<ChatClient> chatClientProvider) {
        this.objectMapper = objectMapper;
        this.chatClientProvider = chatClientProvider;
    }

    /** 测试用 */
    DesignerAgentPlanGenerator(ObjectMapper objectMapper, ObjectProvider<ChatClient> chatClientProvider, boolean ignored) {
        this.objectMapper = objectMapper;
        this.chatClientProvider = chatClientProvider;
    }

    public GenerateResult generate(
            String scenario,
            String baseBpmnXml,
            String selectedElementId,
            String issuesJson,
            String userAnswer) {
        return generate(scenario, baseBpmnXml, selectedElementId, issuesJson, userAnswer, null);
    }

    public GenerateResult generate(
            String scenario,
            String baseBpmnXml,
            String selectedElementId,
            String issuesJson,
            String userAnswer,
            DesignerAgentRun traceRun) {
        return generate(scenario, baseBpmnXml, selectedElementId, issuesJson, userAnswer, traceRun, null);
    }

    public GenerateResult generate(
            String scenario,
            String baseBpmnXml,
            String selectedElementId,
            String issuesJson,
            String userAnswer,
            DesignerAgentRun traceRun,
            String previousEditPlanJson) {
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return GenerateResult.empty("AI ChatClient 未配置");
        }
        DesignerAgentToolTraceContext.bind(traceRun);
        try {
            String prompt = buildPrompt(
                    scenario, baseBpmnXml, selectedElementId, issuesJson, userAnswer, previousEditPlanJson);
            String raw = client.prompt()
                    .user(prompt)
                    .options(ToolCallingChatOptions.builder()
                            .toolNames(DesignerAgentToolScope.DiscoveryToolNames))
                    .call()
                    .content();
            if (StringUtils.isBlank(raw)) {
                return GenerateResult.empty("模型未返回内容");
            }
            try {
                return parseResponse(raw, objectMapper);
            } catch (Exception first) {
                log.warn("EditPlan parse failed (first pass): {} | raw={}", first.getMessage(), truncate(raw, 500));
                try {
                    return parseResponse(raw, LenientJsonMapper);
                } catch (Exception lenient) {
                    log.warn("EditPlan parse failed (lenient pass): {} | raw={}", lenient.getMessage(), truncate(raw, 500));
                    return retryJsonOnly(client, prompt, first.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("EditPlan generate failed: {}", e.getMessage());
            return GenerateResult.empty(e.getMessage());
        } finally {
            DesignerAgentToolTraceContext.clear();
        }
    }

    private GenerateResult retryJsonOnly(ChatClient client, String originalPrompt, String parseError) {
        try {
            String retryRaw = client.prompt()
                    .user("""
                            上一次回复无法解析为 JSON（%s）。
                            请仅输出一个 JSON 对象（EditPlan schema），不要 markdown 代码块、不要任何解释文字。
                            原任务:
                            %s
                            """.formatted(parseError, originalPrompt))
                    .options(ToolCallingChatOptions.builder())
                    .call()
                    .content();
            if (StringUtils.isBlank(retryRaw)) {
                return GenerateResult.empty("模型未返回可解析的 EditPlan JSON");
            }
            try {
                return parseResponse(retryRaw, objectMapper);
            } catch (Exception strict) {
                return parseResponse(retryRaw, LenientJsonMapper);
            }
        } catch (Exception second) {
            log.warn("EditPlan generate failed (retry): {}", second.getMessage());
            return GenerateResult.empty("无法解析 EditPlan：" + second.getMessage());
        }
    }

    public String explainOnly(String scenario, String baseBpmnXml, String selectedElementId) {
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return "AI 未启用";
        }
        String prompt = """
                你是 Kiwi BPMN 设计助手。用户希望理解当前流程，不要修改 BPMN。
                用中文简洁解释流程做什么、主要步骤与组件。
                用户问题: %s
                选中元素 id: %s
                当前 BPMN XML（截断）:
                %s
                """.formatted(
                nullToEmpty(scenario),
                nullToEmpty(selectedElementId),
                truncate(baseBpmnXml, 48000));
        try {
            return client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            return "解释失败: " + e.getMessage();
        }
    }

    private GenerateResult parseResponse(String raw, ObjectMapper mapper) throws Exception {
        Exception last = null;
        for (String json : extractJsonCandidates(raw)) {
            try {
                return parseJsonPayload(json, mapper);
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalArgumentException("未找到可解析的 JSON 对象");
    }

    private GenerateResult parseJsonPayload(String json, ObjectMapper mapper) throws Exception {
        JsonNode node = mapper.readTree(json);
        String summary = textOr(node, "summary", null);
        String thinking = textOr(node, "thinking", null);
        JsonNode planNode = node.get("editPlan");
        if (planNode == null || planNode.isNull()) {
            planNode = node;
        }
        EditPlan plan = null;
        if (planNode.has("operations") || planNode.has("processId")) {
            plan = mapper.treeToValue(planNode, EditPlan.class);
        }
        return new GenerateResult(plan, summary, thinking);
    }

    private String buildPrompt(
            String scenario,
            String baseBpmnXml,
            String selectedElementId,
            String issuesJson,
            String userAnswer,
            String previousEditPlanJson) {
        return """
                你是 Kiwi BPMN 设计器 Agent。根据用户意图产出 EditPlan（JSON），禁止直接输出 BPMN XML。
                必须使用 MCP 工具发现 componentId（bpmComp_aiPage 等），禁止臆造。
                EditPlan schema:
                {"summary":"给用户看的计划摘要（2-6句中文，面向业务用户，禁止 nodeId/componentId 等内部标识）","thinking":"简短推理（可选）",
                 "editPlan":{"processId":"可空","summary":"与顶层 summary 一致的用户可读说明",
                  "operations":[
                    {"op":"addNode","node":{"id":"...","type":"serviceTask","name":"...","componentId":"...","parameters":{}}},
                    {"op":"updateNode","nodeId":"...","patch":{"parameters":{"key":"value"}}},
                    {"op":"removeNode","nodeId":"..."},
                    {"op":"addFlow","flow":{"id":"...","sourceRef":"...","targetRef":"...","condition":""}},
                    {"op":"removeFlow","flowId":"..."},
                    {"op":"addNode","node":{...},"afterRef":"锚点节点id"},
                    {"op":"setProcessMeta","name":"流程名"}
                  ]}}
                仅输出 JSON。operations 有序执行。
                用户场景: %s
                选中元素: %s
                当前 BPMN XML:
                %s
                校验问题（修复时参考）: %s
                用户补充: %s
                上一版 EditPlan（用户未批准，仅供参考）: %s
                """.formatted(
                nullToEmpty(scenario),
                nullToEmpty(selectedElementId),
                truncate(baseBpmnXml, 48000),
                nullToEmpty(issuesJson),
                nullToEmpty(userAnswer),
                nullToEmpty(previousEditPlanJson));
    }

    static String extractJsonPayload(String raw) {
        List<String> candidates = extractJsonCandidates(raw);
        return candidates.isEmpty() ? stripFence(raw).trim() : candidates.getFirst();
    }

    /**
     * 从 LLM 回复中提取候选 JSON 对象。跳过 prose 中的占位符（如 {@code {null}}），优先含 editPlan/operations 的对象。
     */
    static List<String> extractJsonCandidates(String raw) {
        String t = stripFence(raw);
        List<String> objects = findBalancedJsonObjects(t);
        if (objects.isEmpty()) {
            return List.of(t);
        }
        List<String> viable = objects.stream()
                .filter(DesignerAgentPlanGenerator::looksLikeJsonObject)
                .sorted(Comparator.comparingInt(DesignerAgentPlanGenerator::scoreJsonCandidate).reversed())
                .toList();
        if (!viable.isEmpty()) {
            return viable;
        }
        return objects.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    static List<String> findBalancedJsonObjects(String text) {
        List<String> results = new ArrayList<>();
        for (int start = 0; start < text.length(); start++) {
            if (text.charAt(start) != '{') {
                continue;
            }
            String extracted = extractBalancedObject(text, start);
            if (extracted != null && !extracted.isEmpty()) {
                results.add(extracted);
            }
        }
        return results;
    }

    static String extractBalancedObject(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return text.substring(start);
    }

    private static boolean looksLikeJsonObject(String json) {
        return json.indexOf(':') >= 0;
    }

    private static int scoreJsonCandidate(String json) {
        int score = json.length();
        if (json.contains("editPlan")) {
            score += 10_000;
        }
        if (json.contains("operations")) {
            score += 5_000;
        }
        if (json.contains("summary")) {
            score += 1_000;
        }
        return score;
    }

    private static String stripFence(String raw) {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('\n');
            int end = t.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return t.substring(start + 1, end).trim();
            }
        }
        return t;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : fallback;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "\n... [truncated]";
    }

    public record GenerateResult(EditPlan editPlan, String summary, String thinkingTrace) {
        static GenerateResult empty(String msg) {
            return new GenerateResult(null, msg, null);
        }
    }
}
