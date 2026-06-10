package com.kiwi.project.bpm.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 Camunda Element Template JSON 导入为 {@link BpmComponent} 草稿。
 */
public final class ElementTemplateImporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ElementTemplateImporter() {}

    public static BpmComponent importDraft(String json, String parentId) {
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException("template JSON 不能为空");
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            return toComponent(root, parentId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 Element Template JSON: " + e.getMessage(), e);
        }
    }

    public static List<BpmComponent> importMany(String jsonArrayOrObject, String parentId) {
        try {
            JsonNode root = MAPPER.readTree(jsonArrayOrObject);
            if (root.isArray()) {
                List<BpmComponent> out = new ArrayList<>();
                for (JsonNode item : root) {
                    out.add(toComponent(item, parentId));
                }
                return out;
            }
            return List.of(toComponent(root, parentId));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 Element Template JSON: " + e.getMessage(), e);
        }
    }

    private static BpmComponent toComponent(JsonNode root, String parentId) {
        BpmComponent c = new BpmComponent();
        c.setName(text(root, "name"));
        c.setDescription(text(root, "description"));
        c.setGroup(text(root, "category"));
        c.setVersion(String.valueOf(root.path("version").asInt(1)));
        c.setSourceKey(text(root, "id"));
        c.setType(BpmComponent.Type.SpringBean);
        c.setParentId(parentId);

        JsonNode execution = root.get("execution");
        if (execution != null && execution.has("value")) {
            String expr = execution.get("value").asText("");
            if (expr.startsWith("${") && expr.endsWith("}")) {
                c.setKey(expr.substring(2, expr.length() - 1));
            }
        }
        if (StringUtils.isBlank(c.getKey())) {
            c.setKey(c.getSourceKey());
        }

        List<BpmComponentParameter> inputs = new ArrayList<>();
        List<BpmComponentParameter> outputs = new ArrayList<>();
        JsonNode properties = root.get("properties");
        if (properties != null && properties.isArray()) {
            for (JsonNode prop : properties) {
                BpmComponentParameter param = toParameter(prop);
                JsonNode binding = prop.get("binding");
                String bindingType = binding != null ? binding.path("type").asText("") : "";
                if (bindingType.contains("output")) {
                    outputs.add(param);
                } else {
                    inputs.add(param);
                }
            }
        }
        c.setInputParameters(inputs);
        c.setOutputParameters(outputs);
        return c;
    }

    private static BpmComponentParameter toParameter(JsonNode prop) {
        BpmComponentParameter p = new BpmComponentParameter();
        JsonNode binding = prop.get("binding");
        if (binding != null) {
            p.setKey(binding.path("name").asText(null));
        }
        p.setName(text(prop, "label"));
        p.setDescription(text(prop, "description"));
        if (prop.has("value")) {
            p.setDefaultValue(prop.get("value").asText());
        }
        if ("Boolean".equalsIgnoreCase(text(prop, "type"))) {
            p.setHtmlType("CheckBox");
        }
        return p;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return StringUtils.isBlank(s) ? null : s;
    }
}
