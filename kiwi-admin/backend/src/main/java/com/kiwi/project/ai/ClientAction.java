package com.kiwi.project.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端可执行的动作。{@code type} 与 {@code params} 键名由前后端约定（如 navigate、toolbar、bpmnXml、appendComponent）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientAction {

    public static final String TYPE_NAVIGATE = "navigate";
    public static final String TYPE_TOOLBAR = "toolbar";
    public static final String TYPE_BPMN_XML = "bpmnXml";
    public static final String TYPE_APPEND_COMPONENT = "appendComponent";

    public static final String PARAM_PATH = "path";
    public static final String PARAM_QUERY_PARAMS = "queryParams";
    public static final String PARAM_TOOLBAR_COMMAND = "toolbarCommand";
    public static final String PARAM_TOOLBAR_OPTIONS = "toolbarOptions";
    public static final String PARAM_XML = "xml";
    public static final String PARAM_COMPONENT_ID = "componentId";
    public static final String PARAM_SOURCE_ELEMENT_ID = "sourceElementId";

    private String type;
    private Map<String, Object> params;

    public static ClientAction of(String type, Map<String, Object> params) {
        ClientAction action = new ClientAction();
        action.setType(type);
        action.setParams(params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>());
        return action;
    }

    public static ClientAction navigate(String path, Map<String, String> queryParams) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(PARAM_PATH, path != null ? path.trim() : "");
        params.put(PARAM_QUERY_PARAMS,
                queryParams != null ? new LinkedHashMap<>(queryParams) : new LinkedHashMap<>());
        return of(TYPE_NAVIGATE, params);
    }

    public static ClientAction toolbar(String toolbarCommand, Map<String, Object> toolbarOptions) {
        if (toolbarCommand == null || toolbarCommand.isBlank()) {
            throw new IllegalArgumentException("toolbarCommand 不能为空。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(PARAM_TOOLBAR_COMMAND, toolbarCommand.trim());
        params.put(PARAM_TOOLBAR_OPTIONS,
                toolbarOptions != null ? new LinkedHashMap<>(toolbarOptions) : new LinkedHashMap<>());
        return of(TYPE_TOOLBAR, params);
    }

    public static ClientAction bpmnXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("xml 不能为空。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(PARAM_XML, xml);
        return of(TYPE_BPMN_XML, params);
    }

    public static ClientAction appendComponent(String componentId, String sourceElementId) {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId 不能为空。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(PARAM_COMPONENT_ID, componentId.trim());
        if (sourceElementId != null && !sourceElementId.isBlank()) {
            params.put(PARAM_SOURCE_ELEMENT_ID, sourceElementId.trim());
        }
        return of(TYPE_APPEND_COMPONENT, params);
    }
}
