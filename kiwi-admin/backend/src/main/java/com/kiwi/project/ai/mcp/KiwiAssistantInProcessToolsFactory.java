package com.kiwi.project.ai.mcp;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 助手前端动作工具（{@code assistant_navigate}、{@code assistant_designer_*}）的进程内回调工厂。
 * <p>
 * 须与 {@link com.kiwi.project.ai.AssistantClientActionContext} 同线程登记 actions，故仅挂在
 * {@link KiwiAdminAiMcpConfiguration} 的 {@code kiwiChatClient} 上，不走 MCP HTTP 回环。
 * 不得注册为容器级 {@link ToolCallbackProvider} Bean，否则 Spring AI MCP Server 的
 * {@code ToolCallbackConverterAutoConfiguration} 会将其并入 MCP 工具列表，与 ChatClient 进程内挂载重复。
 */
@Component
public class KiwiAssistantInProcessToolsFactory {

    private final ApplicationContext applicationContext;

    public KiwiAssistantInProcessToolsFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ToolCallbackProvider createToolCallbackProvider() {
        Object[] beans = KiwiSpringAiToolBeanCollector.collectAssistantToolBeans(applicationContext);
        if (beans.length == 0) {
            return () -> new ToolCallback[0];
        }
        return MethodToolCallbackProvider.builder()
                .toolObjects(beans)
                .build();
    }
}
