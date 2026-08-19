package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.WriteWorkflowIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 设计器入口意图：talk（闲聊/解释）或 modify（创建/修改流程）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated(since = "2026-08", forRemoval = true)
public class AssistantIntentService {

    private static final String SystemPrompt = """
            你是 Kiwi BPM 设计器助手的意图分类器。
            根据用户最近消息判断意图，只输出 JSON：{"intent":"talk"} 或 {"intent":"modify"}。
            - modify：用户要创建、修改、删除、重排流程图/工作流/节点/连线，或补充改图约束。
            - talk：解释概念、询问现状、闲聊、与改图无关的问题。
            不要输出其它字段或 Markdown。
            """;

    private static final Pattern ModifyHint = Pattern.compile(
            "创建|新建|生成|添加|追加|删除|移除|修改|改成|更新|画|流程|工作流|网关|节点|连线|审批|订单");

    private static final Pattern TalkHint = Pattern.compile(
            "什么意思|是什么|解释|为什么|怎么看|如何理解|介绍一下|帮我看看");

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;

    public WriteWorkflowIntent determineIntent(List<Message> messages) {
        String lastUser = lastUserText(messages);
        if (StringUtils.isBlank(lastUser)) {
            return WriteWorkflowIntent.Talk;
        }
        WriteWorkflowIntent fromLlm = tryLlm(lastUser);
        if (fromLlm != null) {
            return fromLlm;
        }
        return heuristic(lastUser);
    }

    private WriteWorkflowIntent tryLlm(String lastUser) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return null;
        }
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SystemPrompt),
                    new UserMessage(lastUser)));
            var generation = model.call(prompt).getResult();
            if (generation == null || generation.getOutput() == null) {
                return null;
            }
            String text = generation.getOutput().getText();
            if (StringUtils.isBlank(text)) {
                return null;
            }
            String json = text.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JsonNode node = objectMapper.readTree(json);
            String intent = node.path("intent").asText("");
            if ("modify".equalsIgnoreCase(intent)) {
                return WriteWorkflowIntent.Modify;
            }
            if ("talk".equalsIgnoreCase(intent)) {
                return WriteWorkflowIntent.Talk;
            }
        } catch (Exception e) {
            log.debug("intent LLM failed: {}", e.getMessage());
        }
        return null;
    }

    private static WriteWorkflowIntent heuristic(String text) {
        if (TalkHint.matcher(text).find() && !ModifyHint.matcher(text).find()) {
            return WriteWorkflowIntent.Talk;
        }
        if (ModifyHint.matcher(text).find()) {
            return WriteWorkflowIntent.Modify;
        }
        return WriteWorkflowIntent.Talk;
    }

    private static String lastUserText(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof UserMessage um) {
                return um.getText();
            }
        }
        return null;
    }
}
