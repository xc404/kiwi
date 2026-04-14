package com.kiwi.project.system.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 助手侧与菜单路由相关的 Spring AI 工具（如登记前端跳转）；业务 CRUD 优先通过 {@link org.springframework.ai.tool.annotation.Tool}
 * 标注在 {@link org.springframework.web.bind.annotation.RestController} 上暴露。
 */
@Service
@RequiredArgsConstructor
public class MenuAssistantTools {



    private final MenuAssistantActionContext menuAssistantActionContext;
    private final ObjectMapper objectMapper;

    @Tool(
            name = "assistant_navigate",
            description = "登记前端跳转到应用内页面。routePath 必须与 auth_menus 返回的菜单 path 一致（可先查菜单再跳转）。queryParamsJson 可选，为 JSON 对象字符串，例如 {\"groupCode\":\"sys_user_sex\"}。")
    public String navigate(String routePath, String queryParamsJson) {
        Map<String, String> query;
        try {
            query = parseQueryParamsJson(queryParamsJson);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        Optional<String> err = menuAssistantActionContext.addNavigate(routePath, query);
        if (err.isPresent()) {
            return err.get();
        }
        return "已登记前端跳转：" + routePath.trim()
                + (query.isEmpty() ? "。" : "，查询参数=" + query + "。");
    }

    private Map<String, String> parseQueryParamsJson(String queryParamsJson) {
        if (queryParamsJson == null || queryParamsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode root = objectMapper.readTree(queryParamsJson.trim());
            if (!root.isObject()) {
                throw new IllegalArgumentException("queryParamsJson 须为 JSON 对象，例如 {\"groupCode\":\"xxx\"}。");
            }
            Map<String, String> out = new LinkedHashMap<>();
            root.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (v != null && v.isValueNode()) {
                    out.put(e.getKey(), v.asText());
                }
            });
            return out;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("queryParamsJson 解析失败，须为合法 JSON 对象。");
        }
    }
}
