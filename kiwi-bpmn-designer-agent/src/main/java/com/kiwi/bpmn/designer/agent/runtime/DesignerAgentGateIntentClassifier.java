package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 用人机闸门上下文 + 用户原话，由 LLM 判定 accept / reject（及拒绝说明）。
 * 按钮触发的 {@code confirmed} 不经此类，由 Session 直接处理。
 */
@Component
@Slf4j
public class DesignerAgentGateIntentClassifier {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatClient> chatClientProvider;

    @Autowired
    public DesignerAgentGateIntentClassifier(
            ObjectMapper objectMapper,
            @Qualifier("designerAgentChatClient") ObjectProvider<ChatClient> chatClientProvider) {
        this.objectMapper = objectMapper;
        this.chatClientProvider = chatClientProvider;
    }

    /** 测试用 */
    DesignerAgentGateIntentClassifier(ObjectMapper objectMapper, ObjectProvider<ChatClient> chatClientProvider, boolean ignored) {
        this.objectMapper = objectMapper;
        this.chatClientProvider = chatClientProvider;
    }

    public DesignerAgentGateIntentDecision classify(DesignerAgentGateKind kind, DesignerAgentRun run, String userMessage) {
        if (StringUtils.isBlank(userMessage)) {
            return DesignerAgentGateIntentDecision.failed("用户消息不能为空");
        }
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return DesignerAgentGateIntentDecision.failed("AI ChatClient 未配置，无法解析意图，请使用界面按钮");
        }
        try {
            String raw = client.prompt().user(buildPrompt(kind, run, userMessage.trim())).call().content();
            if (StringUtils.isBlank(raw)) {
                return DesignerAgentGateIntentDecision.failed("模型未返回意图判定");
            }
            return parseModelJson(raw, userMessage.trim());
        } catch (Exception e) {
            log.warn("gate intent classify failed: {}", e.getMessage());
            return DesignerAgentGateIntentDecision.failed("意图解析失败: " + e.getMessage());
        }
    }

    DesignerAgentGateIntentDecision parseModelJson(String raw, String userMessageFallback) {
        try {
            String json = DesignerAgentPlanGenerator.extractJsonPayload(raw);
            JsonNode root = objectMapper.readTree(json);
            String decision = root.path("decision").asText("").trim().toLowerCase();
            String feedback = StringUtils.trimToNull(root.path("feedback").asText(null));
            return switch (decision) {
                case "accept", "accepted", "approve", "yes" -> DesignerAgentGateIntentDecision.accept();
                case "reject", "rejected", "no", "decline" -> {
                    String fb = StringUtils.isNotBlank(feedback) ? feedback : userMessageFallback;
                    yield DesignerAgentGateIntentDecision.reject(fb);
                }
                default -> DesignerAgentGateIntentDecision.failed("模型返回未知 decision: " + decision);
            };
        } catch (Exception e) {
            log.warn("gate intent JSON parse failed: {} | raw={}", e.getMessage(), truncate(raw, 400));
            return DesignerAgentGateIntentDecision.failed("无法解析模型意图 JSON");
        }
    }

    private static String buildPrompt(DesignerAgentGateKind kind, DesignerAgentRun run, String userMessage) {
        String gateDesc =
                switch (kind) {
                    case PLAN ->
                            """
                            当前阶段：用户正在查看「变更计划」，需要决定是否按该计划改 BPMN。
                            - accept：用户明确同意执行该计划（批准、可以、执行吧等）。
                            - reject：用户不同意或要求调整；必须在 feedback 中提炼可执行的修改意见（可合并用户原话）。
                            若用户仅补充细节但仍希望按现有计划执行，判 accept。
                            """;
                    case PREVIEW ->
                            """
                            当前阶段：候选 BPMN 已在画布预览，用户需决定是否保存到流程定义。
                            - accept：默认倾向。用户未明确拒绝、或表示满意/可以/保存/继续，均判 accept。
                            - reject：仅当用户明确不要此版本、要求回退或重新生成；feedback 写清要改什么。
                            闲聊、补充说明但未否定预览时，判 accept。
                            """;
                };
        StringBuilder ctx = new StringBuilder();
        if (StringUtils.isNotBlank(run.getAssistantReply())) {
            ctx.append("助手最近说明：").append(truncate(run.getAssistantReply(), 800)).append('\n');
        }
        if (StringUtils.isNotBlank(run.getPlanDisplayJson())) {
            ctx.append("计划摘要(JSON)：").append(truncate(run.getPlanDisplayJson(), 1200)).append('\n');
        }
        if (StringUtils.isNotBlank(run.getAskMessage())) {
            ctx.append("待回答提示：").append(truncate(run.getAskMessage(), 400)).append('\n');
        }
        return """
                你是 BPM 设计器人机闸门意图分类器。只输出 JSON，不要 markdown，不要解释。
                输出格式：{"decision":"accept"|"reject","feedback":string|null}
                decision=reject 时 feedback 必填（简短、中文、可给下游改图 Agent 用）。

                %s

                上下文：
                %s

                用户原话：
                %s
                """
                .formatted(gateDesc, ctx.length() > 0 ? ctx : "（无）\n", userMessage);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max) + "…";
    }
}
