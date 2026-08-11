package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从场景文本抽取检索词：短 LLM 负责语义归一化，规则负责兜底与常用同义词扩展。
 */
@Component
@Slf4j
public class AssistantKeywordExtractor {

    private static final Pattern TokenPattern = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z][A-Za-z0-9_-]{1,}");
    private static final int MaxKeywords = 12;
    private static final String SystemPrompt = """
            你是 Kiwi 工作流组件与流程模板的检索查询解析器。
            请从用户的应用场景中提取适合搜索组件和模板的短关键词及领域标签。
            只输出 JSON，不要 Markdown 或解释：
            {"keywords":["..."],"tags":["..."]}
            要求：
            - keywords 与 tags 合计最多 8 个；
            - 使用简短、可检索的中文或英文术语；
            - 保留明确的产品/协议/技术名，如 HTTP、Kafka、JDBC、Slack；
            - 不要输出“流程”“工作流”“场景”“实现”等泛词。
            """;

    private final AssistantProperties assistantProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public AssistantKeywordExtractor(
            AssistantProperties assistantProperties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatModel> chatModelProvider) {
        this.assistantProperties = assistantProperties;
        this.objectMapper = objectMapper;
        this.chatModelProvider = chatModelProvider;
    }

    public List<String> extract(String scenario) {
        Set<String> out = new LinkedHashSet<>();
        if (StringUtils.isBlank(scenario)) {
            return List.of();
        }
        out.addAll(extractWithLlm(scenario));
        extractByRules(scenario, out);
        expandSynonyms(scenario, out);
        return out.stream().limit(MaxKeywords).toList();
    }

    private List<String> extractWithLlm(String scenario) {
        if (!assistantProperties.isEnabled()) {
            return List.of();
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return List.of();
        }
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SystemPrompt),
                    new UserMessage(scenario)));
            var generation = chatModel.call(prompt).getResult();
            if (generation == null || generation.getOutput() == null) {
                return List.of();
            }
            String content = generation.getOutput().getText();
            if (StringUtils.isBlank(content)) {
                return List.of();
            }
            KeywordExtraction extraction =
                    objectMapper.readValue(stripCodeFence(content), KeywordExtraction.class);
            Set<String> normalized = new LinkedHashSet<>();
            addNormalized(normalized, extraction.keywords());
            addNormalized(normalized, extraction.tags());
            return normalized.stream().limit(MaxKeywords).toList();
        } catch (Exception e) {
            log.debug("短 LLM 抽取工作流检索词失败，回退规则抽取: {}", e.getMessage());
            return List.of();
        }
    }

    private void extractByRules(String scenario, Set<String> out) {
        Matcher m = TokenPattern.matcher(scenario);
        while (m.find()) {
            String t = m.group().toLowerCase(Locale.ROOT);
            if (t.length() >= 2 && !isGenericKeyword(t)) {
                out.add(t);
            }
        }
    }

    public String extractAsJson(String scenario) {
        try {
            return objectMapper.writeValueAsString(extract(scenario));
        } catch (Exception e) {
            return "[]";
        }
    }

    private void expandSynonyms(String scenario, Set<String> out) {
        String s = scenario.toLowerCase(Locale.ROOT);
        if (containsAny(s, "http", "接口", "api", "请求", "webhook")) {
            out.add("http");
            out.add("httprequest");
        }
        if (containsAny(s, "邮件", "email", "mail")) {
            out.add("email");
            out.add("mail");
        }
        if (containsAny(s, "shell", "命令", "脚本", "bash")) {
            out.add("shell");
        }
        if (containsAny(s, "mongo", "mongodb")) {
            out.add("mongo");
        }
        if (containsAny(s, "jdbc", "sql", "数据库")) {
            out.add("jdbc");
            out.add("sql");
        }
        if (containsAny(s, "slack", "钉钉", "通知", "告警", "notify")) {
            out.add("notify");
            out.add("slack");
        }
        if (containsAny(s, "kafka")) {
            out.add("kafka");
        }
        if (containsAny(s, "s3", "对象存储")) {
            out.add("s3");
        }
    }

    private void addNormalized(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String normalized = StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
            if (normalized.length() >= 2 && !isGenericKeyword(normalized)) {
                target.add(normalized);
            }
        }
    }

    private String stripCodeFence(String content) {
        String value = content.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        return firstLineEnd >= 0 && lastFence > firstLineEnd
                ? value.substring(firstLineEnd + 1, lastFence).trim()
                : value;
    }

    private boolean isGenericKeyword(String value) {
        return Set.of("流程", "工作流", "场景", "实现", "设计", "生成", "创建", "帮我", "一个")
                .contains(value);
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record KeywordExtraction(List<String> keywords, List<String> tags) {
    }
}
