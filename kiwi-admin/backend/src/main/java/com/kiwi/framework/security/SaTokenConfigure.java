package com.kiwi.framework.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token：全局登录校验 + 匿名白名单。
 * <p>
 * 白名单路径（无需 token）：
 * <ul>
 *   <li>{@code /auth/signin}、{@code /auth/signout} — 登录/登出</li>
 *   <li>{@code /swagger-ui/**}、{@code /v3/api-docs/**}、{@code /swagger-ui.html} — OpenAPI/Swagger</li>
 *   <li>{@code /engine-rest/**} — Camunda REST（请结合网关或 Camunda 自身鉴权在生产环境收紧）</li>
 *   <li>{@code /camunda/**} — Camunda Webapp</li>
 *   <li>{@code /sse}、{@code /message} — Spring AI MCP（WebMVC + SSE）协议端点；本机回环与外部 MCP 客户端均需匿名可访问</li>
 *   <li>{@code /error} — Spring Boot 错误页</li>
 * </ul>
 * <p>
 * 浏览器跨域预检 {@code OPTIONS} 不带 Token，必须放行，否则 CORS 预检失败。
 */
@Service
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/**")
                .notMatch("/auth/signin")
                .notMatch("/auth/signout")
                .notMatch("/swagger-ui/**")
                .notMatch("/swagger-ui.html")
                .notMatch("/v3/api-docs/**")
                .notMatch("/v3/api-docs")
                .notMatch("/engine-rest/**")
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
