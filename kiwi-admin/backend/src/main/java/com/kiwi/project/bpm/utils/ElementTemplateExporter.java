package com.kiwi.project.bpm.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 将 {@link BpmComponent} 导出为 Camunda Element Template JSON。
 */
public final class ElementTemplateExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ElementTemplateExporter() {}

    public static ObjectNode export(BpmComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("component 不能为空");
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("$schema", "https://unpkg.com/@camunda/element-templates-json-schema/resources/schema.json");
        root.put("name", StringUtils.defaultIfBlank(component.getName(), component.getKey()));
        root.put("id", StringUtils.defaultIfBlank(component.getSourceKey(), component.getKey()));
        root.put("description", StringUtils.defaultString(component.getDescription()));
        root.put("version", parseVersion(component.getVersion()));
        root.put("category", StringUtils.defaultIfBlank(component.getGroup(), "General"));

        ObjectNode appliesTo = MAPPER.createObjectNode();
        appliesTo.put("value", "bpmn:ServiceTask");
        root.set("appliesTo", MAPPER.createArrayNode().add("bpmn:ServiceTask"));
        root.set("elementType", appliesTo);

        if (StringUtils.isNotBlank(component.getKey())) {
            ObjectNode execution = MAPPER.createObjectNode();
            execution.put("type", "delegateExpression");
            execution.put("value", "${" + component.getKey() + "}");
            root.set("execution", execution);
        }

        ArrayNode properties = MAPPER.createArrayNode();
        appendParameters(properties, component.getInputParameters(), true);
        appendParameters(properties, component.getOutputParameters(), false);
        root.set("properties", properties);
        return root;
    }

    public static String exportJson(BpmComponent component) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(export(component));
        } catch (Exception e) {
            throw new IllegalStateException("导出 Element Template 失败", e);
        }
    }

    private static void appendParameters(ArrayNode properties, List<BpmComponentParameter> params, boolean input) {
        if (params == null) {
            return;
        }
        for (BpmComponentParameter p : params) {
            if (p == null || p.isHidden() || StringUtils.isBlank(p.getKey())) {
                continue;
            }
            ObjectNode prop = MAPPER.createObjectNode();
            prop.put("label", StringUtils.defaultIfBlank(p.getName(), p.getKey()));
            prop.put("type", mapType(p));
            if (StringUtils.isNotBlank(p.getDescription())) {
                prop.put("description", p.getDescription());
            }
            ObjectNode binding = MAPPER.createObjectNode();
            binding.put("type", input ? "camunda:inputParameter" : "camunda:outputParameter");
            binding.put("name", p.getKey());
            prop.set("binding", binding);
            if (StringUtils.isNotBlank(p.getDefaultValue())) {
                prop.put("value", p.getDefaultValue());
            }
            properties.add(prop);
        }
    }

    private static String mapType(BpmComponentParameter p) {
        if ("CheckBox".equalsIgnoreCase(p.getHtmlType())) {
            return "Boolean";
        }
        if (p.isArray()) {
            return "String";
        }
        return "String";
    }

    private static int parseVersion(String version) {
        if (StringUtils.isBlank(version)) {
            return 1;
        }
        String digits = version.replaceAll("[^0-9].*$", "").replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
