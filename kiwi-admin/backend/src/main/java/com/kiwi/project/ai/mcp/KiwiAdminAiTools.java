package com.kiwi.project.ai.mcp;

import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.project.system.dao.SysDictDao;
import com.kiwi.project.system.dao.SysDictGroupDao;
import com.kiwi.project.system.entity.SysDict;
import com.kiwi.project.system.entity.SysDictGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Kiwi 管理后台 AI 可调用的工具（经 Spring AI 注册为 Tool，并由 MCP Server 一并暴露）。
 */
@Service
@RequiredArgsConstructor
public class KiwiAdminAiTools {

    private static final Pattern IDENT = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    public static final String SYSTEM_PROMPT = """
            你是 Kiwi 管理后台的 AI 助手。你可以通过工具完成系统操作，不要编造已执行的操作。
            当用户需要维护「字典分类 / 字典项」时，优先调用工具；需要打开字典管理界面或刚创建/修改完字典后用户希望去页面查看时，调用 openDictManagementPage(groupCode)。
            若信息不足（例如缺少合法的 groupCode），先向用户追问，不要随意调用工具。
            """;

    private final AiAssistantToolContext toolContext;
    private final SysDictGroupDao sysDictGroupDao;
    private final SysDictDao sysDictDao;

    @Tool(description = "创建字典分类（字典组）。groupCode 须为英文字母开头，仅含字母数字下划线；groupName 为显示名称。")
    public String createDictGroup(String groupCode, String groupName, String remark) {
        if (!canEditDict()) {
            return "当前账号无字典编辑权限（system:dict:edit），未创建。";
        }
        if (groupCode == null || groupName == null) {
            return "groupCode、groupName 不能为空。";
        }
        groupCode = groupCode.trim();
        groupName = groupName.trim();
        if (!IDENT.matcher(groupCode).matches() || groupName.isEmpty()) {
            return "参数不合法：groupCode 须匹配 [a-zA-Z][a-zA-Z0-9_]*，groupName 非空。";
        }
        if (sysDictGroupDao.existsById(groupCode)) {
            return "字典分类「" + groupCode + "」已存在，未重复创建。如需查看可在界面打开该分类。";
        }
        SysDictGroup g = new SysDictGroup();
        g.setGroupCode(groupCode);
        g.setGroupName(groupName);
        g.setRemark(trimToNull(remark));
        g.setStatus(SysDictGroup.StatusEnabled);
        sysDictGroupDao.save(g);
        return "已创建字典分类「" + groupName + "」（groupCode=" + groupCode + "）。";
    }

    @Tool(description = "在已有字典分类下新增字典项。groupCode 为分类编码，code 为项编码，name 为显示名称。")
    public String createDictItem(String groupCode, String code, String name, String remark) {
        if (!canEditDict()) {
            return "当前账号无字典编辑权限（system:dict:edit），未添加。";
        }
        if (groupCode == null || code == null || name == null) {
            return "groupCode、code、name 不能为空。";
        }
        groupCode = groupCode.trim();
        code = code.trim();
        name = name.trim();
        if (!IDENT.matcher(groupCode).matches() || !IDENT.matcher(code).matches() || name.isEmpty()) {
            return "参数不合法：groupCode、code 须为合法标识符，name 非空。";
        }
        if (!sysDictGroupDao.existsById(groupCode)) {
            return "字典分类「" + groupCode + "」不存在，请先创建分类或使用正确编码。";
        }
        SysDict d = new SysDict();
        d.setGroupCode(groupCode);
        d.setCode(code);
        d.setName(name);
        d.setRemark(trimToNull(remark));
        d.setDictSort(SysDict.DefaultSort);
        sysDictDao.save(d);
        return "已在分类「" + groupCode + "」下添加字典项「" + name + "」（code=" + code + "）。";
    }

    @Tool(description = "登记前端跳转到「系统-字典管理」页面并展开指定字典分类（groupCode）。调用后用户界面将打开对应页面。")
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

    private static boolean canEditDict() {
        try {
            StpUtil.checkPermission("system:dict:edit");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
