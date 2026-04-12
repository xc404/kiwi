package com.kiwi.project.ai.mcp;

import com.kiwi.project.ai.AiAssistantResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次助手请求内，工具回调与 HTTP 线程绑定，用于收集需前端执行的动作（如路由跳转）。
 * <p>
 * 与 Spring AI {@code ToolCallback} 在同一线程执行，可在 MCP 与 {@link org.springframework.ai.chat.client.ChatClient} 中共用。
 */
@Component
public class AiAssistantToolContext {

    private static final ThreadLocal<List<AiAssistantResponse.ClientAction>> ACTIONS =
            ThreadLocal.withInitial(ArrayList::new);

    public void beginRequest() {
        ACTIONS.remove();
    }

    public void addNavigateToDict(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            return;
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("groupCode", groupCode.trim());
        ACTIONS.get().add(AiAssistantResponse.navigate("/default/system/dict", query));
    }

    public List<AiAssistantResponse.ClientAction> drainActions() {
        try {
            return List.copyOf(ACTIONS.get());
        } finally {
            ACTIONS.remove();
        }
    }
}
