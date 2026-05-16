package com.kiwi.cryoems.bpm.support;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 从 Camunda 流程变量读取 movie/task 契约及扩展字段。
 */
public final class WorkflowVariableReader {

    public static final String DEFAULT_MOTION_VERSION = "1.4.5";

    private WorkflowVariableReader() {}

    public static String resolveWorkDir(DelegateExecution execution) {
        Map<String, Object> task = readMap(execution, "task");
        return firstNonBlank(readText(execution, "work_dir"), readFromMap(task, "work_dir"));
    }

    public static String requireText(DelegateExecution execution, String... keys) {
        for (String key : keys) {
            String value = readText(execution, key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        throw new IllegalArgumentException("流程变量缺失: " + String.join(" / ", keys));
    }

    public static String readText(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    public static String readFromMap(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }



    public static String resolveMotionVersion(DelegateExecution execution) {
        String version = readText(execution, "motionVersion");
        return StringUtils.hasText(version) ? version.trim() : DEFAULT_MOTION_VERSION;
    }

    public static String resolveMicroscope(DelegateExecution execution) {
        Map<String, Object> task = readMap(execution, "task");
        return firstNonBlank(readText(execution, "microscope"), readFromMap(task, "microscope"));
    }


    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
