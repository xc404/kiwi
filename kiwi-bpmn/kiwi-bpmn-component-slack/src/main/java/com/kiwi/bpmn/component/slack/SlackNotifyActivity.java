package com.kiwi.bpmn.component.slack;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ComponentDescription(
        name = "Slack 通知",
        group = "通知",
        version = "1.0",
        description = "向 Slack Incoming Webhook 发送 JSON 消息；Webhook URL 建议用项目环境变量 ${SLACK_WEBHOOK_URL}",
        inputs = {
                @ComponentParameter(key = "webhook_url", description = "Slack Incoming Webhook URL", required = true),
                @ComponentParameter(key = "text", description = "消息正文", required = true),
                @ComponentParameter(key = "channel", description = "可选频道覆盖"),
                @ComponentParameter(
                        key = "connectTimeoutSeconds",
                        schema = @Schema(defaultValue = "10"))
        },
        outputs = {
                @ComponentParameter(key = "statusCode", schema = @Schema(defaultValue = "statusCode")),
                @ComponentParameter(key = "responseBody", schema = @Schema(defaultValue = "responseBody"))
        })
@Component("slackNotify")
public class SlackNotifyActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String webhookUrl = ExecutionUtils.requireStringInputVariable(execution, "webhook_url");
        String text = ExecutionUtils.requireStringInputVariable(execution, "text");
        String channel = ExecutionUtils.getStringInputVariable(execution, "channel").orElse(null);
        int timeout =
                ExecutionUtils.getIntInputVariable(execution, "connectTimeoutSeconds").orElse(10);

        String payload = buildPayload(text, channel);

        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String statusVar = ExecutionUtils.getOutputVariableName(execution, "statusCode");
        if (statusVar != null && !statusVar.isBlank()) {
            execution.setVariable(statusVar, response.statusCode());
        }
        String bodyVar = ExecutionUtils.getOutputVariableName(execution, "responseBody");
        if (bodyVar != null && !bodyVar.isBlank()) {
            execution.setVariable(bodyVar, response.body());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Slack Webhook 失败: HTTP " + response.statusCode());
        }
    }

    String buildPayload(String text, String channel) {
        String escapedText = escapeJson(text);
        if (channel == null || channel.isBlank()) {
            return "{\"text\":\"" + escapedText + "\"}";
        }
        String escapedChannel = escapeJson(channel);
        return "{\"channel\":\"" + escapedChannel + "\",\"text\":\"" + escapedText + "\"}";
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
