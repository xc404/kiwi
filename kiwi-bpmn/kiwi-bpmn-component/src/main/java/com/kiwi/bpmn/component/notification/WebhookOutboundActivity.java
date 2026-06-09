package com.kiwi.bpmn.component.notification;

import com.kiwi.bpmn.component.activity.HttpRequestActivity;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Webhook 出站：默认 POST，复用 {@link HttpRequestActivity} 执行 HTTP。
 */
@ComponentDescription(
        name = "Webhook 出站",
        group = "通知",
        version = "1.0",
        description = "向指定 URL 发送 HTTP 请求（默认 POST），适用于 Webhook 回调",
        inputs = {
                @ComponentParameter(key = "url", description = "Webhook URL", required = true),
                @ComponentParameter(
                        key = "method",
                        description = "HTTP 方法，默认 POST",
                        schema = @Schema(defaultValue = "POST")),
                @ComponentParameter(key = "headers", description = "可选 JSON 对象请求头"),
                @ComponentParameter(key = "body", description = "请求体"),
                @ComponentParameter(
                        key = "connectTimeoutSeconds",
                        schema = @Schema(defaultValue = "10")),
                @ComponentParameter(
                        key = "readTimeoutSeconds",
                        schema = @Schema(defaultValue = "30"))
        },
        outputs = {
                @ComponentParameter(key = "statusCode", schema = @Schema(defaultValue = "statusCode")),
                @ComponentParameter(key = "responseBody", schema = @Schema(defaultValue = "responseBody")),
                @ComponentParameter(key = "responseHeaders", schema = @Schema(defaultValue = "responseHeaders"))
        })
@Component("webhookOutbound")
@RequiredArgsConstructor
public class WebhookOutboundActivity implements JavaDelegate {

    private final HttpRequestActivity httpRequestActivity;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        if (!(execution instanceof ActivityExecution activityExecution)) {
            throw new IllegalStateException("Webhook 组件需要 ActivityExecution 上下文");
        }
        httpRequestActivity.execute(activityExecution);
    }
}
