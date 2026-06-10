package com.kiwi.bpmn.component.rabbitmq;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@ComponentDescription(
        name = "RabbitMQ 发布",
        group = "消息",
        version = "1.0",
        description = "向 RabbitMQ 交换机发布单条消息",
        inputs = {
                @ComponentParameter(key = "host", description = "主机", required = true),
                @ComponentParameter(
                        key = "port",
                        description = "端口",
                        schema = @Schema(defaultValue = "5672")),
                @ComponentParameter(key = "username", description = "用户名", required = true),
                @ComponentParameter(key = "password", description = "密码"),
                @ComponentParameter(
                        key = "virtual_host",
                        description = "vhost",
                        schema = @Schema(defaultValue = "/")),
                @ComponentParameter(key = "exchange", description = "交换机", required = true),
                @ComponentParameter(key = "routing_key", description = "路由键", required = true),
                @ComponentParameter(key = "body", description = "消息体", required = true)
        })
@Component("rabbitMqPublish")
public class RabbitMqPublishActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String host = require(execution, "host");
        int port = ExecutionUtils.getIntInputVariable(execution, "port").orElse(5672);
        String username = require(execution, "username");
        String password = ExecutionUtils.getStringInputVariable(execution, "password").orElse("");
        String vhost = ExecutionUtils.getStringInputVariable(execution, "virtual_host").orElse("/");
        String exchange = require(execution, "exchange");
        String routingKey = require(execution, "routing_key");
        String body = require(execution, "body");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(vhost);

        try (Connection connection = factory.newConnection();
                Channel channel = connection.createChannel()) {
            channel.basicPublish(
                    exchange,
                    routingKey,
                    null,
                    body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String require(DelegateExecution execution, String key) {
        return ExecutionUtils.getStringInputVariable(execution, key)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 " + key + " 不能为空"));
    }
}
