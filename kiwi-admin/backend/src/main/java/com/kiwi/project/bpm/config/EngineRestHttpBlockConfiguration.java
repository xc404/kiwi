package com.kiwi.project.bpm.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 默认阻断 {@code /engine-rest} HTTP 入口；仅当 {@code kiwi.bpm.engine-rest-http-enabled=true} 时不注册本 Filter。
 */
@Configuration
@EnableConfigurationProperties(KiwiBpmProperties.class)
public class EngineRestHttpBlockConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kiwi.bpm", name = "engine-rest-http-enabled", havingValue = "false", matchIfMissing = true)
    public FilterRegistrationBean<Filter> engineRestHttpBlockFilter() {
        Filter blockFilter = (ServletRequest request, ServletResponse response, FilterChain chain)
                -> handleEngineRestBlock(request, response, chain);

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(blockFilter);
        bean.addUrlPatterns("/engine-rest/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

    private void handleEngineRestBlock(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setContentType(MediaType.TEXT_PLAIN_VALUE);
        httpResponse.getWriter().write("Operaton /engine-rest HTTP is disabled (set KIWI_BPM_ENGINE_REST_HTTP_ENABLED=true to enable)");
    }
}
