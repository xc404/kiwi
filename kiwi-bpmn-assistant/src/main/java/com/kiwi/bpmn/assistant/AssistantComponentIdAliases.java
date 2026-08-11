package com.kiwi.bpmn.assistant;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code classpath_*} 与 {@code plugin_*} 组件 id 互备（与 BpmComponentService 解析规则一致）。
 */
public final class AssistantComponentIdAliases {

    private AssistantComponentIdAliases() {
    }

    public static String alternateId(String componentId) {
        if (StringUtils.isBlank(componentId)) {
            return null;
        }
        if (componentId.startsWith("classpath_")) {
            return "plugin_" + componentId.substring("classpath_".length());
        }
        if (componentId.startsWith("plugin_")) {
            return "classpath_" + componentId.substring("plugin_".length());
        }
        return null;
    }

    public static List<String> idAndAlternate(String componentId) {
        List<String> ids = new ArrayList<>(2);
        if (StringUtils.isNotBlank(componentId)) {
            ids.add(componentId);
            String alt = alternateId(componentId);
            if (alt != null) {
                ids.add(alt);
            }
        }
        return ids;
    }

    public static boolean sameComponent(String left, String right) {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        String alt = alternateId(left);
        return alt != null && alt.equals(right);
    }

    public static String beanName(String componentId) {
        if (componentId == null) {
            return "unknown";
        }
        int separator = componentId.indexOf('_');
        return separator >= 0 ? componentId.substring(separator + 1) : componentId;
    }
}
