package com.kiwi.cryoems.bpm.movie.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.util.Map;

final class MovieDelegateValueHelper {
    private MovieDelegateValueHelper() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value, String variableName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new MovieFatalException("流程变量 " + variableName + " 不是对象结构");
        }
        return (Map<String, Object>) map;
    }

    static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> nestedMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return Map.of();
    }

    static Object readPath(Map<String, Object> map, String... path) {
        Object current = map;
        for (String segment : path) {
            if (!(current instanceof Map<?, ?> cursor)) {
                return null;
            }
            current = cursor.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static String textPath(Map<String, Object> map, String... path) {
        Object value = readPath(map, path);
        return value == null ? null : String.valueOf(value);
    }

    static Integer intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Integer intPath(Map<String, Object> map, String... path) {
        return intValue(readPath(map, path));
    }

    static boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s);
        }
        return false;
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s);
        }
        return false;
    }

    static Double doubleValue(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Double doublePath(Map<String, Object> map, String... path) {
        return doubleValue(readPath(map, path));
    }

    @SuppressWarnings("unchecked")
    static Double readNestedDouble(DelegateExecution execution, String rootKey, String childKey) {
        Object root = execution.getVariable(rootKey);
        if (root instanceof Map<?, ?> map) {
            Object child = ((Map<String, Object>) map).get(childKey);
            return doubleValue(child);
        }
        return null;
    }
}
