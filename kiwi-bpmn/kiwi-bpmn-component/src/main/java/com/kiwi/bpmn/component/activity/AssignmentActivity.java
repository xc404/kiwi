package com.kiwi.bpmn.component.activity;

import org.bson.Document;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量设置流程变量：流程变量 {@code assignments} 为 {@code List<Assignment>}；
 * 仍兼容运行时传入元素为 {@link Map} 的 List，以及 JSON 数组字符串（解析后转为 Assignment）。
 */
@ComponentDescription(
        name = "赋值",
        group = "流程控制",
        version = "1.0",
        description = "assignments 为 Assignment 列表（key 目标变量名，value 字面量或 ${变量名} 引用）；可与 Map 列表或 JSON 数组字符串互操作",
        inputs = {
                @ComponentParameter(
                        key = "assignments",
                        htmlType = "#text",
                        type = "array",
                        name = "assignments",
                        description = "Assignment 列表（key 目标变量名，value 值）；设计器可用 JSON 数组表示",
                        required = true)
        },
        outputs = {})
@Component("assignmentActivity")
public class AssignmentActivity extends AbstractBpmnActivityBehavior {

    private static final Pattern VAR_REF = Pattern.compile("^\\$\\{([a-zA-Z0-9_]+)}$");

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
                Matcher mat = VAR_REF.matcher(s);
                if (mat.matches()) {
                    String sourceName = mat.group(1);
                    if (!execution.hasVariable(sourceName)) {
                        throw new IllegalArgumentException("赋值引用变量不存在: " + sourceName);
                    }
                    execution.setVariable(targetKey, execution.getVariable(sourceName));
                    continue;
                }
            }
            execution.setVariable(targetKey, valObj);
        }
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
            String t = s.trim();
            if (t.isEmpty()) {
                throw new IllegalArgumentException("流程变量 assignments 不能为空");
            }
            try {
                Document wrapper = Document.parse("{\"items\":" + t + "}");
                Object items = wrapper.get("items");
                if (!(items instanceof List<?> parsed)) {
                    throw new IllegalArgumentException("assignments 字符串须解析为 JSON 数组");
                }
                List<Assignment> out = new ArrayList<>(parsed.size());
                for (Object item : parsed) {
                    out.add(toAssignment(item));
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
