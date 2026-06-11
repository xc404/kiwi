package com.kiwi.framework.security;

import cn.dev33.satoken.application.ApplicationInfo;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token：全局登录校验 + 匿名白名单。
 * <p>
 * 白名单路径（无需 token）均相对于 {@code server.servlet.context-path} 之后的路径书写（如 {@code /auth/signin}），
 * 勿手写 {@code /kiwi/auth/signin}。Sa-Token 通过 {@link ApplicationInfo#routePrefix} 从 {@code request.getRequestURI()}
 * 剥掉 context-path 后再匹配；须在首次请求前赋值（见构造器），否则 {@code WebServerInitializedEvent} 等早期逻辑会误判未登录。
 * <p>
 * 白名单路径：
 * <ul>
 *   <li>{@code /auth/signin}、{@code /auth/signout} — 登录/登出</li>
 *   <li>{@code /user/personal-access-tokens/exchange} — PAT 兑换 Sa-Token（机机）</li>
 *   <li>{@code /swagger-ui/**}、{@code /v3/api-docs/**}、{@code /swagger-ui.html} — OpenAPI/Swagger</li>
 *   <li>{@code /camunda/**} — Camunda Webapp</li>
 *   <li>{@code /sse}、{@code /message} — Spring AI MCP（WebMVC + SSE）协议端点</li>
 *   <li>{@code /error} — Spring Boot 错误页</li>
 * </ul>
 * <p>
 * 浏览器跨域预检 {@code OPTIONS} 不带 Token，必须放行，否则 CORS 预检失败。
 */
@Service
public class SaTokenConfigure implements WebMvcConfigurer {

    public SaTokenConfigure(@Value("${server.servlet.context-path:}") String servletContextPath) {
        String prefix = normalizeContextPath(servletContextPath);
        if (!prefix.isEmpty()) {
            ApplicationInfo.routePrefix = prefix;
        }
    }

    private String normalizeContextPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return "";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/**")
                .notMatch("/auth/signin")
                .notMatch("/auth/signout")
                .notMatch("/user/personal-access-tokens/exchange")
                .notMatch("/swagger-ui/**")
                .notMatch("/swagger-ui.html")
                .notMatch("/v3/api-docs/**")
                .notMatch("/v3/api-docs")
                .notMatch("/camunda/**")
                .notMatch("/sse")
                .notMatch("/message")
                .notMatch("/error")
                .check(r -> StpUtil.checkLogin())) {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                return super.preHandle(request, response, handler);
            }
        }).addPathPatterns("/**");
    }
}
