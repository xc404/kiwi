package com.kiwi.project.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 {@code /ai/assistant} 请求内，与工具回调同线程收集需前端执行的 {@link ClientAction}。
 * 仅负责登记；参数与业务合法性由调用方（如 MCP 工具）自行处理。
 */
@Component
public class AssistantClientActionContext {

    private static final ThreadLocal<List<ClientAction>> ACTIONS =
            ThreadLocal.withInitial(ArrayList::new);

    public void beginRequest() {
        ACTIONS.remove();
    }

    public void addClientAction(ClientAction action) {
        ACTIONS.get().add(action);
    }

    public List<ClientAction> drainActions() {
        try {
            return List.copyOf(ACTIONS.get());
        } finally {
            ACTIONS.remove();
        }
    }
}
