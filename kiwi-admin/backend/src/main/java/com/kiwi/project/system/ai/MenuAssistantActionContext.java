package com.kiwi.project.system.ai;

import com.kiwi.project.ai.AiAssistantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单次助手请求内，工具回调与 HTTP 线程绑定，用于收集需前端执行的动作（如按菜单路由跳转）。
 * <p>
 * 与 Spring AI {@code ToolCallback} 在同一线程执行，可在 MCP 与 {@link org.springframework.ai.chat.client.ChatClient} 中共用。
 * 跳转路径须与当前用户侧栏菜单（{@code /auth/menus}）中配置的 {@code path} 一致。
 */
@Component
@RequiredArgsConstructor
public class MenuAssistantActionContext {

    private static final ThreadLocal<List<AiAssistantResponse.ClientAction>> ACTIONS =
            ThreadLocal.withInitial(ArrayList::new);

    private final MenuNavigatePathValidator menuNavigatePathValidator;

    public void beginRequest() {
        ACTIONS.remove();
    }

    /**
     * 登记一次前端路由跳转；path 须通过菜单路由校验（与 {@code auth_menus} 中 path 一致）。
     *
     * @return 校验失败时的错误说明；成功则为 empty
     */
    public Optional<String> addNavigate(String path, Map<String, String> queryParams) {
        Optional<String> err = menuNavigatePathValidator.validate(path);
        if (err.isPresent()) {
            return err;
        }
        Map<String, String> q = queryParams != null ? new LinkedHashMap<>(queryParams) : new LinkedHashMap<>();
        ACTIONS.get().add(AiAssistantResponse.navigate(path.trim(), q));
        return Optional.empty();
    }

    public List<AiAssistantResponse.ClientAction> drainActions() {
        try {
            return List.copyOf(ACTIONS.get());
        } finally {
            ACTIONS.remove();
        }
    }
}
