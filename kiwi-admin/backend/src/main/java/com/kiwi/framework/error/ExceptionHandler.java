package com.kiwi.framework.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import com.kiwi.project.ai.AiModelErrorUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dreamlu.mica.core.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;


@ConditionalOnWebApplication
@ControllerAdvice
public class ExceptionHandler
{

    private final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    @ResponseBody
    @org.springframework.web.bind.annotation.ExceptionHandler(NonTransientAiException.class)
    public R<?> handleNonTransientAi(
            NonTransientAiException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.warn("大模型调用失败: {}", e.getMessage());
        return jsonFail(response, request, R.fail(AiModelErrorUtils.toUserMessage(e.getMessage())));
    }

    @ResponseBody
    @org.springframework.web.bind.annotation.ExceptionHandler
    public R<?> processException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        logger.error(e.getMessage(), e);
        if (e instanceof NotLoginException) {
            return jsonFail(response, request, R.fail(SystemCode.TokenError, e.getMessage()));
        }
        if (e instanceof SaTokenException) {
            return jsonFail(response, request, R.fail(SystemCode.NotPermission, e.getMessage()));
        }
        return jsonFail(response, request, R.fail(e.getMessage()));
    }

    /**
     * Spring AI MCP（/sse 等）在映射阶段会预设 {@code text/event-stream}；
     * 全局异常若仍返回 {@link R}，会因无 SSE 转换器触发 HttpMessageNotWritableException。
     */
    private R<?> jsonFail(HttpServletResponse response, HttpServletRequest request, R<?> body) {
        if (expectsEventStream(response, request) && !response.isCommitted()) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        return body;
    }

    private boolean expectsEventStream(HttpServletResponse response, HttpServletRequest request) {
        if (containsEventStream(response.getContentType())) {
            return true;
        }
        if (containsEventStream(request.getHeader("Accept"))) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/sse");
    }

    private boolean containsEventStream(String value) {
        return StringUtils.hasText(value) && value.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

}
