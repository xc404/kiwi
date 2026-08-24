package com.kiwi.project.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端可执行的动作。{@code type} 与 {@code params} 键名由前后端约定（如 navigate）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientAction {

    public static final String TYPE_NAVIGATE = "navigate";
    public static final String PARAM_PATH = "path";
    public static final String PARAM_QUERY_PARAMS = "queryParams";

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
}
