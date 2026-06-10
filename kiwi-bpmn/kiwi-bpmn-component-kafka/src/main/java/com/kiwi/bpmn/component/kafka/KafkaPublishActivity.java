package com.kiwi.bpmn.component.kafka;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.Future;

@ComponentDescription(
        name = "Kafka 生产",
        group = "消息",
        version = "1.0",
        description = "向 Kafka Topic 发送单条消息（同步 flush）；Broker 地址建议用项目环境变量",
        inputs = {
                @ComponentParameter(key = "bootstrap_servers", description = "bootstrap.servers", required = true),
                @ComponentParameter(key = "topic", description = "Topic", required = true),
                @ComponentParameter(key = "value", description = "消息体", required = true),
                @ComponentParameter(key = "key", description = "可选消息 Key"),
                @ComponentParameter(
                        key = "acks",
                        description = "producer acks",
                        schema = @Schema(defaultValue = "all"))
        },
        outputs = {
                @ComponentParameter(key = "partition", schema = @Schema(defaultValue = "partition")),
                @ComponentParameter(key = "offset", schema = @Schema(defaultValue = "offset"))
        })
@Component("kafkaPublish")
public class KafkaPublishActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String bootstrap = require(execution, "bootstrap_servers");
        String topic = require(execution, "topic");
        String value = require(execution, "value");
        String key = ExecutionUtils.getStringInputVariable(execution, "key").orElse(null);
        String acks = ExecutionUtils.getStringInputVariable(execution, "acks").orElse("all");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, acks);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record =
                    key == null || key.isBlank()
                            ? new ProducerRecord<>(topic, value)
                            : new ProducerRecord<>(topic, key, value);
            Future<RecordMetadata> future = producer.send(record);
            producer.flush();
            RecordMetadata meta = future.get();

            String partitionVar = ExecutionUtils.getOutputVariableName(execution, "partition");
            if (partitionVar != null && !partitionVar.isBlank()) {
                execution.setVariable(partitionVar, meta.partition());
            }
            String offsetVar = ExecutionUtils.getOutputVariableName(execution, "offset");
            if (offsetVar != null && !offsetVar.isBlank()) {
                execution.setVariable(offsetVar, meta.offset());
            }
        }
    }

    private static String require(DelegateExecution execution, String key) {
        return ExecutionUtils.getStringInputVariable(execution, key)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 " + key + " 不能为空"));
    }
}
