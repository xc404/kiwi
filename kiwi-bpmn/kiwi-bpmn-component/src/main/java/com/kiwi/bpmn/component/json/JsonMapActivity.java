package com.kiwi.bpmn.component.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import org.operaton.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从流程变量 JSON（字符串或 Map/Document）按 JSON Pointer 提取字段并写入多个流程变量。
 */
@Component("jsonMapActivity")
@ComponentDescription(
        name = "JSON 映射",
        group = "通用",
        version = "1.0",
        description = "从 source 变量解析 JSON，按 mappings 中的 JSON Pointer 写入多个流程变量",
        inputs = {
                @ComponentParameter(
                        key = "source",
                        name = "source",
                        description = "源流程变量名；值为 JSON 字符串、Map 或 Mongo Document",
                        required = true),
                @ComponentParameter(
                        key = "mappings",
                        name = "mappings",
                        description = "JSON 数组 [{\"key\":\"目标变量\",\"value\":\"/json/pointer\"}, ...] 或对象 {目标: pointer}",
                        required = true,
                        htmlType = "assignments-editor")
        },
        outputs = {})
public class JsonMapActivity extends AbstractBpmnActivityBehavior {

    private final JsonMapExecutor executor;

    public JsonMapActivity() {
        this.executor = new JsonMapExecutor();
    }

    JsonMapActivity(JsonMapExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        Object sourceValue = resolveSourceValue(execution);

        String mappingsRaw = ExecutionUtils.getStringInputVariable(execution, "mappings")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 mappings 不能为空"));

        JsonNode root = executor.toJsonNode(sourceValue);
        List<JsonMapExecutor.MappingEntry> mappings = executor.parseMappings(mappingsRaw);
        Map<String, Object> writes = executor.applyMappings(root, mappings);
        for (Map.Entry<String, Object> entry : writes.entrySet()) {
            execution.setVariable(entry.getKey(), entry.getValue());
        }
        super.leave(execution);
    }

    private Object resolveSourceValue(ActivityExecution execution) {
        Optional<Object> mapped = ExecutionUtils.getInputVariableAtPath(execution, "source");
        if (mapped.isEmpty()) {
            throw new IllegalArgumentException("流程变量 source 不能为空");
        }
        Object value = mapped.get();
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("流程变量 source 不能为空");
            }
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return trimmed;
            }
            Object referenced = execution.getVariable(trimmed);
            if (referenced == null) {
                throw new IllegalArgumentException("source 指向的流程变量不存在: " + trimmed);
            }
            return referenced;
        }
        return value;
    }
}
