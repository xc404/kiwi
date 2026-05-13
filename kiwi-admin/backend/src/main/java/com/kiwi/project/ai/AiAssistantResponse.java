package com.kiwi.project.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiAssistantResponse {

    /** 展示给用户的文本 */
    private String content;

    /**
     * 前端可执行的动作（如跳转）。path 为应用内路由，须与当前用户菜单（auth_menus）中的 path 一致，例如 /system/dict。
     */
    private List<ClientAction> actions;
}
