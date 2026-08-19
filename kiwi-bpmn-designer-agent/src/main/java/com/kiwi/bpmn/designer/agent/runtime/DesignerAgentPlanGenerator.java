package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolScope;
import com.kiwi.bpmn.designer.agent.mcp.DesignerAgentToolTraceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
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
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return GenerateResult.empty("AI ChatClient 未配置");
        }
        DesignerAgentToolTraceContext.bind(traceRun);
        try {
            String prompt = buildPrompt(scenario, baseBpmnXml, selectedElementId, issuesJson, userAnswer);
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
                return parseResponse(raw);
            } catch (Exception first) {
                log.warn("EditPlan parse failed (first pass): {}", first.getMessage());
                return retryJsonOnly(client, prompt, first.getMessage());
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
            return parseResponse(retryRaw);
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

    private GenerateResult parseResponse(String raw) throws Exception {
        String json = extractJsonPayload(raw);
        JsonNode node = objectMapper.readTree(json);
        String summary = textOr(node, "summary", null);
        String thinking = textOr(node, "thinking", null);
        JsonNode planNode = node.get("editPlan");
        if (planNode == null || planNode.isNull()) {
            planNode = node;
        }
        EditPlan plan = null;
        if (planNode.has("operations") || planNode.has("processId")) {
            plan = objectMapper.treeToValue(planNode, EditPlan.class);
        }
        return new GenerateResult(plan, summary, thinking);
    }

    private String buildPrompt(
            String scenario,
            String baseBpmnXml,
            String selectedElementId,
            String issuesJson,
            String userAnswer) {
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
                """.formatted(
                nullToEmpty(scenario),
                nullToEmpty(selectedElementId),
                truncate(baseBpmnXml, 48000),
                nullToEmpty(issuesJson),
                nullToEmpty(userAnswer));
    }

    static String extractJsonPayload(String raw) {
        String t = stripFence(raw);
        if (t.startsWith("{") || t.startsWith("[")) {
            return t;
        }
        int start = t.indexOf('{');
        if (start < 0) {
            return t;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
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
                    return t.substring(start, i + 1);
                }
            }
        }
        return t.substring(start);
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
