package com.kiwi.project.system.ai;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将原 {@link MenuAssistantTools} 上的 MCP 能力以 REST + OpenAPI 暴露，供 Springdoc 与工具回调共用。
 */
@SaCheckLogin
@RestController
@RequestMapping("/ai/mcp/assistant")
@RequiredArgsConstructor
@Tag(name = "AI助手动作", description = "前端跳转登记等（与 MCP 工具 operationId 一致）")
public class AssistantActionsCtl {

    private final MenuAssistantTools menuAssistantTools;

    @PostMapping("/navigate")
    @Operation(
            operationId = "assistant_navigate",
            summary = "登记前端跳转到应用内页面",
            description = "routePath 必须与 auth_menus 返回的菜单 path 一致（可先查菜单再跳转）。queryParamsJson 可选，为 JSON 对象字符串，例如 {\"groupCode\":\"sys_user_sex\"}。")
    public String navigate(
            @RequestParam String routePath,
            @RequestParam(required = false) String queryParamsJson) {
        return menuAssistantTools.navigate(routePath, queryParamsJson);
    }
}
