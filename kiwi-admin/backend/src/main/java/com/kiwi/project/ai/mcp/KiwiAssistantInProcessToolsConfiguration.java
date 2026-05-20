package com.kiwi.project.ai.mcp;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 助手前端动作工具（{@code assistant_navigate}、{@code assistant_designer_*}）的进程内回调。
 * <p>
 * 须与 {@link com.kiwi.project.ai.AssistantClientActionContext} 同线程登记 actions，故挂在
 * {@link KiwiAdminAiMcpConfiguration} 的 {@code kiwiChatClient} 上，不走 MCP HTTP 回环。
 * MCP Server 侧不再注册同名工具，避免重复定义。
 */
@Configuration
public class KiwiAssistantInProcessToolsConfiguration {

    @Bean
    public ToolCallbackProvider kiwiAssistantInProcessToolCallbackProvider(ApplicationContext applicationContext) {
        Object[] beans = KiwiSpringAiToolBeanCollector.collectAssistantToolBeans(applicationContext);
        if (beans.length == 0) {
            return () -> new ToolCallback[0];
        }
        return MethodToolCallbackProvider.builder()
                .toolObjects(beans)
                .build();
    }
}
