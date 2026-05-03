package com.kiwi.project.ai.mcp;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * 本机 MCP（SSE）客户端，供 {@link KiwiAdminAiMcpConfiguration} 等通过
 * {@link org.springframework.ai.mcp.SyncMcpToolCallbackProvider} 与 MCP Server 对齐工具列表。
 */
@Configuration
public class KiwiLocalMcpClientConfiguration {

    @Bean
    @Lazy
    public McpSyncClient kiwiLocalMcpSyncClient(
            @Value("${kiwi.ai.mcp.loopback-base-url}") String loopbackBaseUrl,
            @Value("${spring.ai.mcp.server.sse-endpoint:/sse}") String sseEndpoint,
            ObjectMapper objectMapper) {
        String base = loopbackBaseUrl.endsWith("/")
                ? loopbackBaseUrl.substring(0, loopbackBaseUrl.length() - 1)
                : loopbackBaseUrl;
        var transport = HttpClientSseClientTransport.builder(base)
                .sseEndpoint(sseEndpoint.startsWith("/") ? sseEndpoint : "/" + sseEndpoint)
                .objectMapper(objectMapper)
                .customizeRequest(b -> {
                    try {
                        if (StpUtil.isLogin()) {
                            b.header("Authorization", "Bearer " + StpUtil.getTokenValue());
                        }
                    } catch (Throwable ignored) {
                        // 无登录态时回环仍可能匿名失败，由调用方处理
                    }
                })
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofMinutes(3)).build();
    }
}
