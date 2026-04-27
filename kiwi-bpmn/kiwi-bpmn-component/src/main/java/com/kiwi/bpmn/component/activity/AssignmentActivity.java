package com.kiwi.bpmn.component.activity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.kiwi.common.utils.JsonUtils;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.el.ExpressionManager;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 批量设置流程变量：流程变量 {@code assignments} 为 {@code List<Assignment>}；
 * 仍兼容运行时传入元素为 {@link Map} 的 List，以及 JSON 数组字符串（解析后转为 Assignment）。
 */
@ComponentDescription(
        name = "赋值组件",
        group = "流程控制",
        version = "1.0",
        description = "assignments 为 Assignment 列表（key 目标变量名，value 字面量或整段 ${...} JUEL 表达式）；可与 Map 列表或 JSON 数组字符串互操作",
        inputs = {
                @ComponentParameter(
                        key = "assignments",
                        htmlType = "assignments-editor",
                        type = "array",
                        name = "assignments",
                        description = "Assignment 列表（key 目标变量名，value 字面量或整段 ${...} JUEL）；设计器为列表编辑器，存储为 JSON 数组字符串",
                        required = true,
                        schema = @Schema(defaultValue = "[]"))
        },
        outputs = {})
@Component("assignmentActivity")
public class AssignmentActivity extends AbstractBpmnActivityBehavior {

    /** 与前端 {@code assignments-editor} 一致：整段为 {@code ${...}} 时按 JUEL 求值；否则为字面量字符串。 */
    private static final Pattern JUEL_WRAPPED = Pattern.compile("^\\$\\{[\\s\\S]*}$");


    @Override
    public void execute(ActivityExecution execution) throws Exception {
        List<Assignment> list = resolveAssignmentsListImpl(execution);
        applyAssignments(execution, list);
        super.leave(execution);
    }

    void applyAssignments(DelegateExecution execution, List<Assignment> assignments) {
        for (Assignment a : assignments) {
            if (a.getKey() == null) {
                throw new IllegalArgumentException("assignments 项缺少 key");
            }
            String targetKey = a.getKey().trim();
            if (targetKey.isEmpty()) {
                throw new IllegalArgumentException("assignments 项 key 不能为空");
            }
            Object valObj = a.getValue();
            if (valObj instanceof String s) {
                execution.setVariable(targetKey, resolveStringValue(s, execution));
            } else {
                execution.setVariable(targetKey, valObj);
            }
        }
    }

    /**
     * 否则视为字面量（含普通文本、数字形式的字符串等）。
     */
    Object resolveStringValue(String s, DelegateExecution execution) {
        if (s != null && JUEL_WRAPPED.matcher(s).matches()) {
            ProcessEngine processEngine = execution.getProcessEngine();
            ExpressionManager expressionManager = ((ProcessEngineImpl) processEngine)
                    .getProcessEngineConfiguration()
                    .getExpressionManager();
            return expressionManager.createExpression(s).getValue(execution);
        }
        return s;
    }

    static List<Assignment> resolveAssignmentsListImpl(ActivityExecution execution) {
        Object raw = execution.getVariable("assignments");
        if (raw == null) {
            throw new IllegalArgumentException("流程变量 assignments 不能为空");
        }
        if (raw instanceof List<?> list) {
            List<Assignment> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toAssignment(item));
            }
            return out;
        }
        if (raw instanceof String s) {
            String t = unwrapOuterJsonStringIfQuoted(s.trim());
            if (t.isEmpty()) {
                throw new IllegalArgumentException("流程变量 assignments 不能为空");
            }
            try {
                JsonNode root = JsonUtils.readTree("{\"items\":" + t + "}");
                JsonNode items = root.get("items");
                if (items == null || !items.isArray()) {
                    throw new IllegalArgumentException("assignments 字符串须解析为 JSON 数组");
                }
                List<Assignment> out = new ArrayList<>(items.size());
                for (JsonNode item : items) {
                    out.add(
                            toAssignment(
                                    JsonUtils.convertValue(
                                            item, new TypeReference<Map<String, Object>>() {})));
                }
                return out;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("assignments 不是合法 JSON 数组: " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException("assignments 须为 List 或可解析为 JSON 数组的字符串");
    }

    /**
     * Camunda / 序列化侧可能把 JSON 数组又包成一段 JSON 字符串（带外层引号），此时需先解析出一层文本再按数组处理。
     * 若本身是数组文本 {@code [...]}，则 Jackson 解析为 {@link JsonNode#isArray()}，本方法原样返回。
     */
    static String unwrapOuterJsonStringIfQuoted(String t) {
        if (t == null || t.isEmpty()) {
            return t == null ? "" : t;
        }
        try {
            JsonNode n = JsonUtils.readTree(t);
            if (n != null && n.isTextual()) {
                return n.asText().trim();
            }
        } catch (JsonProcessingException ignored) {
            // 非法 JSON：交给后续拼接 items 时再报错
        }
        return t;
    }

    static Assignment toAssignment(Object item) {
        if (item instanceof Assignment a) {
            if (a.getKey() == null) {
                throw new IllegalArgumentException("assignments 项缺少 key");
            }
            return a;
        }
        if (item instanceof Map<?, ?> m) {
            Object keyObj = m.get("key");
            if (keyObj == null) {
                throw new IllegalArgumentException("assignments 项缺少 key");
            }
            String k = String.valueOf(keyObj).trim();
            if (k.isEmpty()) {
                throw new IllegalArgumentException("assignments 项 key 不能为空");
            }
            return new Assignment(k, m.get("value"));
        }
        throw new IllegalArgumentException("assignments 每一项须为 Assignment 或含 key/value 的 Map");
    }
}
