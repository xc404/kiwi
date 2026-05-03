package com.kiwi.project.ai;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AiAssistantResponse {

    /** 展示给用户的文本 */
    private String content;

    /**
     * 前端可执行的动作（如跳转）。path 为应用内路由，须与当前用户菜单（auth_menus）中的 path 一致，例如 /system/dict。
     */
    private List<ClientAction> actions;

    @Data
    public static class ClientAction {
        private String type;
        private String path;
        private Map<String, String> queryParams;
    }

    public static ClientAction navigate(String path, Map<String, String> queryParams) {
        ClientAction a = new ClientAction();
        a.setType("navigate");
        a.setPath(path);
        a.setQueryParams(queryParams != null ? queryParams : new LinkedHashMap<>());
        return a;
    }
}
