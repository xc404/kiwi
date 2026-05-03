package com.kiwi.project.ai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 本机 MCP（SSE）回环客户端在应用启动早期可能遇到端口尚未 listen 的竞态，导致 {@code HttpClientSseClientTransport} 刷
 * {@code SSE connection error}。在 WebServer 初始化完成后再做一次轻量 warmup，把首次连接推迟到“可连时刻”。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kiwi.ai.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "kiwi.ai.mcp", name = "warmup-enabled", havingValue = "true", matchIfMissing = true)
public class KiwiLocalMcpWebServerWarmup {

    private final ObjectProvider<McpSyncClient> kiwiLocalMcpSyncClient;
    private final int maxAttempts;
    private final long backoffMillis;

    public KiwiLocalMcpWebServerWarmup(
            ObjectProvider<McpSyncClient> kiwiLocalMcpSyncClient,
            @Value("${kiwi.ai.mcp.warmup-max-attempts:20}") int maxAttempts,
            @Value("${kiwi.ai.mcp.warmup-backoff-ms:150}") long backoffMillis) {
        this.kiwiLocalMcpSyncClient = kiwiLocalMcpSyncClient;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMillis = Math.max(0L, backoffMillis);
    }

    @EventListener(WebServerInitializedEvent.class)
    public void warmupLocalMcpAfterWebServerReady(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                McpSyncClient client = kiwiLocalMcpSyncClient.getObject();
                if (!client.isInitialized()) {
                    client.initialize();
                }
                // 触发一次工具列表拉取：比 ping 更能覆盖 MCP 初始化链路
                client.listTools();
                if (attempt > 1) {
                    log.info("MCP 本机回环 warmup 成功（第 {} 次尝试，webPort={}）", attempt, port);
                }
                return;
            } catch (Exception ex) {
                last = ex;
                if (attempt == 1 || attempt == maxAttempts || (attempt % 5) == 0) {
                    log.warn(
                            "MCP 本机回环 warmup 失败（{}/{}，webPort={}）：{}",
                            attempt,
                            maxAttempts,
                            port,
                            ex.toString());
                }
                if (attempt < maxAttempts && backoffMillis > 0) {
                    try {
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("MCP warmup 被中断（webPort={}）", port);
                        return;
                    }
                }
            }
        }
        log.error("MCP 本机回环 warmup 在 {} 次尝试后仍失败（webPort={}）", maxAttempts, port, last);
    }
}
