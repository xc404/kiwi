package com.kiwi.project.ai.mcp;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * MCP 本机回环鉴权：在异步线程等无 Sa-Token 上下文的场景传播 Bearer Token，
 * 供 MCP Client HTTP 请求与 Server 侧 OpenAPI 工具（{@code @SaCheckLogin}）使用。
 */
public final class KiwiMcpLoopbackAuthSupport {

    private static final ThreadLocal<String> PropagatedToken = new ThreadLocal<>();

    private KiwiMcpLoopbackAuthSupport() {
    }

    /** 在 HTTP 请求线程捕获当前登录 Token（供后续异步任务使用）。 */
    public static String captureCurrentToken() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getTokenValue();
            }
        } catch (Throwable ignored) {
            // 无 Web 上下文时忽略
        }
        return null;
    }

    /** 在指定 Token 作用域内执行任务（通常用于 Designer Agent 异步 Graph）。 */
    public static void runWithToken(String token, Runnable task) {
        if (StringUtils.isBlank(token)) {
            task.run();
            return;
        }
        PropagatedToken.set(token);
        try {
            task.run();
        } finally {
            PropagatedToken.remove();
        }
    }

    /** 解析可用于 MCP 回环的 Bearer Token：当前登录态 → 传播 Token → 当前 HTTP 请求头。 */
    public static Optional<String> resolveBearerToken() {
        try {
            if (StpUtil.isLogin()) {
                return Optional.of(StpUtil.getTokenValue());
            }
        } catch (Throwable ignored) {
            // 继续 fallback
        }
        String propagated = PropagatedToken.get();
        if (StringUtils.isNotBlank(propagated)) {
            return Optional.of(propagated);
        }
        return bearerFromCurrentRequest();
    }

    /** 在可解析 Token 时绑定 Sa-Token 上下文后执行（MCP Server 工具回调）。 */
    public static <T> T callWithResolvedToken(Supplier<T> action) {
        if (StpUtil.isLogin()) {
            return action.get();
        }
        Optional<String> token = resolveBearerToken();
        if (token.isEmpty()) {
            return action.get();
        }
        return SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.setTokenValue(token.get());
            return action.get();
        });
    }

    public static ToolCallback wrapToolCallback(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                return callWithResolvedToken(() -> delegate.call(toolInput));
            }
        };
    }

    private static Optional<String> bearerFromCurrentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return Optional.empty();
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return Optional.empty();
        }
        String authorization = request.getHeader(SaManager.getConfig().getTokenName());
        if (StringUtils.isBlank(authorization)) {
            authorization = request.getHeader("Authorization");
        }
        return extractBearer(authorization);
    }

    private static Optional<String> extractBearer(String authorization) {
        if (StringUtils.isBlank(authorization)) {
            return Optional.empty();
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).trim();
            return StringUtils.isNotBlank(token) ? Optional.of(token) : Optional.empty();
        }
        return Optional.of(trimmed);
    }
}
