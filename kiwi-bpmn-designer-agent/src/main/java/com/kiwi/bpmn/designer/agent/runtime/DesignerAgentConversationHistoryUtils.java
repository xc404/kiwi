package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话消息列表 JSON 的读写（checkpoint / Mongo 投影共用）。
 */
public final class DesignerAgentConversationHistoryUtils {

    private static final ObjectMapper Mapper = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> ListType = new TypeReference<>() {};

    private DesignerAgentConversationHistoryUtils() {
    }

    public static String append(String historyJson, String role, String text) {
        if (StringUtils.isBlank(text)) {
            return historyJson;
        }
        String trimmed = text.trim();
        List<Map<String, String>> list = parse(historyJson);
        if (!list.isEmpty()) {
            Map<String, String> last = list.get(list.size() - 1);
            if (role.equals(last.get("role")) && trimmed.equals(last.get("text"))) {
                return historyJson;
            }
        }
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("role", role);
        entry.put("text", trimmed);
        list.add(entry);
        try {
            return Mapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 conversationHistory 失败", e);
        }
    }

    public static List<Map<String, String>> parse(String historyJson) {
        if (StringUtils.isBlank(historyJson)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, String>> parsed = Mapper.readValue(historyJson, ListType);
            return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<DesignerAgentChatMessage> toChatMessages(String historyJson) {
        List<DesignerAgentChatMessage> out = new ArrayList<>();
        for (Map<String, String> row : parse(historyJson)) {
            if (row == null) {
                continue;
            }
            String role = row.get("role");
            String text = row.get("text");
            if (StringUtils.isBlank(role) || text == null) {
                continue;
            }
            DesignerAgentChatMessage msg = new DesignerAgentChatMessage();
            msg.setRole(role);
            msg.setText(text);
            out.add(msg);
        }
        return out;
    }
}
