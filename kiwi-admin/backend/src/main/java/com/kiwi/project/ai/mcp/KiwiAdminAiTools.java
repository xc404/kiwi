package com.kiwi.project.ai.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 非 REST 的通用助手工具（如前端路由登记）；业务 CRUD 优先通过 {@link org.springframework.ai.tool.annotation.Tool}
 * 标注在 {@link org.springframework.web.bind.annotation.RestController} 上暴露。
 */
@Service
@RequiredArgsConstructor
public class KiwiAdminAiTools {

    private static final Pattern IDENT = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            业务工具名称带前缀（如 dict_*、user_*、bpmPd_*、assistant_openDictManagementPage）；需要打开字典管理界面时调用 assistant_openDictManagementPage。
            若信息不足，先向用户追问，不要随意调用工具。
            """;

    private final AiAssistantToolContext toolContext;

    @Tool(
            name = "assistant_openDictManagementPage",
            description = "登记前端跳转到「系统-字典管理」页面并展开指定字典分类（groupCode）。调用后用户界面将打开对应页面。")
    public String openDictManagementPage(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            return "groupCode 不能为空。";
        }
        groupCode = groupCode.trim();
        if (!IDENT.matcher(groupCode).matches()) {
            return "groupCode 格式不合法。";
        }
        toolContext.addNavigateToDict(groupCode);
        return "已登记前端跳转：字典管理页，定位 groupCode=" + groupCode + "。";
    }
}
