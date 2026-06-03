package com.kiwi.bpmn.component.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.kiwi.common.utils.JsonUtils;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 JSON 根节点按 JSON Pointer 映射写入目标变量名列表。
 */
public class JsonMapExecutor {

    public record MappingEntry(String targetVariable, String jsonPointer) {}

    public List<MappingEntry> parseMappings(String mappingsRaw) {
        if (mappingsRaw == null || mappingsRaw.isBlank()) {
            throw new IllegalArgumentException("流程变量 mappings 不能为空");
        }
        String trimmed = mappingsRaw.trim();
        try {
            if (trimmed.startsWith("[")) {
                return parseArrayMappings(trimmed);
            }
            if (trimmed.startsWith("{")) {
                return parseObjectMappings(trimmed);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("mappings 不是合法 JSON: " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("mappings 必须是 JSON 数组或对象");
    }

    public JsonNode toJsonNode(Object sourceValue) {
        if (sourceValue == null) {
            throw new IllegalArgumentException("source 流程变量不存在或为 null");
        }
        if (sourceValue instanceof String stringValue) {
            if (stringValue.isBlank()) {
                throw new IllegalArgumentException("source 不能为空字符串");
            }
            try {
                return JsonUtils.readTree(stringValue.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("source 不是合法 JSON 字符串: " + e.getMessage(), e);
            }
        }
        if (sourceValue instanceof Document document) {
            return JsonUtils.valueToTree(document);
        }
        if (sourceValue instanceof Map<?, ?> || sourceValue instanceof List<?>) {
            return JsonUtils.valueToTree(sourceValue);
        }
        throw new IllegalArgumentException(
                "source 类型不支持 JSON 映射: " + sourceValue.getClass().getName());
    }

    public Map<String, Object> applyMappings(JsonNode root, List<MappingEntry> mappings) {
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("mappings 至少包含一条映射");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (MappingEntry entry : mappings) {
            if (entry.targetVariable() == null || entry.targetVariable().isBlank()) {
                throw new IllegalArgumentException("mappings 中存在空的目标变量名");
            }
            String pointer = entry.jsonPointer();
            if (pointer == null || pointer.isBlank()) {
                throw new IllegalArgumentException(
                        "映射 " + entry.targetVariable() + " 的 JSON Pointer 不能为空");
            }
            String normalizedPointer = normalizePointer(pointer);
            JsonNode node = root.at(normalizedPointer);
            if (node instanceof MissingNode) {
                throw new IllegalArgumentException(
                        "JSON Pointer 无匹配: target=" + entry.targetVariable() + ", pointer=" + normalizedPointer);
            }
            result.put(entry.targetVariable(), toProcessVariableValue(node));
        }
        return result;
    }

    private String normalizePointer(String pointer) {
        String p = pointer.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return p;
    }

    private Object toProcessVariableValue(JsonNode node) {
        if (node == null || node instanceof NullNode) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(child -> list.add(toProcessVariableValue(child)));
            return list;
        }
        if (node.isObject()) {
            return JsonUtils.convertValue(node, Map.class);
        }
        return node.asText();
    }

    private List<MappingEntry> parseArrayMappings(String json) {
        try {
            List<Map<String, Object>> rows = JsonUtils.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("mappings 数组不能为空");
            }
            List<MappingEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object keyObj = row.get("key");
                Object valueObj = row.get("value");
                if (keyObj == null) {
                    throw new IllegalArgumentException("mappings 数组项缺少 key");
                }
                entries.add(new MappingEntry(String.valueOf(keyObj), valueObj != null ? String.valueOf(valueObj) : ""));
            }
            return entries;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("mappings 数组解析失败: " + e.getMessage(), e);
        }
    }

    private List<MappingEntry> parseObjectMappings(String json) {
        try {
            Map<String, Object> map = JsonUtils.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (map == null || map.isEmpty()) {
                throw new IllegalArgumentException("mappings 对象不能为空");
            }
            List<MappingEntry> entries = new ArrayList<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                entries.add(new MappingEntry(
                        e.getKey(), e.getValue() != null ? String.valueOf(e.getValue()) : ""));
            }
            return entries;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("mappings 对象解析失败: " + e.getMessage(), e);
        }
    }
}
