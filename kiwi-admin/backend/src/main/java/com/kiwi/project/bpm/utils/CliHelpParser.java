package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从常见 GNU/类 GNU 风格 {@code --help} 文本中抽取选项，并生成继承 shell 的 {@link BpmComponent} 元数据：
 * 每个选项对应一个 {@code cli_*} 输入参数，并追加隐藏的 {@code command} 以覆盖父组件「命令行」的 command。
 */
public final class CliHelpParser {

    private static final Pattern LONG_OPT = Pattern.compile("--([a-zA-Z][-a-zA-Z0-9]*)");
    private static final Pattern SHORT_OPT = Pattern.compile("(^|[\\s,])-([a-zA-Z0-9])\\b");
    /** 行内描述列与选项列之间的分隔：至少两个空格 */
    private static final Pattern OPTION_DESC_SPLIT = Pattern.compile(
            "^\\s*(.+?)\\s{2,}([^\\s].*)$");

    private CliHelpParser() {
    }

    public record ParsedOption(
            String longId,
            String primaryLongFlag,
            String shortFlag,
            boolean expectsValue,
            String description
    ) {
    }

    /**
     * @param helpText   命令行执行 {@code --help} 的输出全文
     * @param executable 可执行文件或子命令前缀（写入 command 模板字面量部分，可含空格）
     * @param name       组件显示名
     * @param key        组件 key；为空则从 name 推导
     * @param group      分组
     * @param description 组件说明
     * @param shellParentId shell 父组件在库中的 id（如 classpath_shell）
     */
    public static BpmComponent buildComponent(
            String helpText,
            String executable,
            String name,
            String key,
            String group,
            String description,
            String shellParentId
    ) {
        List<ParsedOption> options = parseOptions(helpText);
        String compName = StringUtils.isNotBlank(name) ? name : executable;
        String compKey = StringUtils.isNotBlank(key) ? key : slugKey(compName);
        BpmComponent c = new BpmComponent();
        c.setParentId(shellParentId);
        c.setKey(compKey);
        c.setName(compName);
        c.setDescription(StringUtils.isNotBlank(description) ? description : ("CLI: " + executable));
        c.setGroup(StringUtils.isNotBlank(group) ? group : "common");
        c.setType(BpmComponent.Type.SpringBean);
        List<BpmComponentParameter> inputs = new ArrayList<>();
        Set<String> usedKeys = new LinkedHashSet<>();
        usedKeys.add("command");
        usedKeys.add("directory");
        usedKeys.add("waitFlag");
        usedKeys.add("redirectError");
        usedKeys.add("cleanEnv");
        List<NamedOption> named = new ArrayList<>();
        for (ParsedOption o : options) {
            String base = "cli_" + o.longId().replace('-', '_');
            String paramKey = uniquifyKey(base, usedKeys);
            named.add(new NamedOption(o, paramKey));
            BpmComponentParameter p = new BpmComponentParameter();
            p.setKey(paramKey);
            p.setName(o.primaryLongFlag() + (StringUtils.isNotBlank(o.shortFlag()) ? " (-" + o.shortFlag() + ")" : ""));
            p.setDescription(o.description());
            p.setGroup("CLI");
            p.setImportant(true);
            p.setHtmlType(o.expectsValue() ? "#text" : "CheckBox");
            p.setRequired(false);
            if (!o.expectsValue()) {
                p.setDefaultValue("false");
            }
            inputs.add(p);
        }
        BpmComponentParameter command = new BpmComponentParameter();
        command.setKey("command");
        command.setName("command");
        command.setDescription("由 CLI 选项拼装的完整命令（覆盖父组件 command；Camunda 输入中可使用 JUEL 表达式）");
        command.setGroup("CLI");
        command.setHidden(true);
        command.setImportant(false);
        command.setHtmlType("#text");
        command.setDefaultValue(buildCommandTemplate(executable, named));
        inputs.add(command);
        c.setInputParameters(inputs);
        c.setOutputParameters(null);
        return c;
    }

    static List<ParsedOption> parseOptions(String helpText) {
        List<ParsedOption> out = new ArrayList<>();
        Set<String> seenLong = new LinkedHashSet<>();
        if (helpText == null) {
            return out;
        }
        for (String raw : helpText.split("\r?\n")) {
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            String stripped = line.stripLeading();
            if (stripped.regionMatches(true, 0, "usage:", 0, 6)) {
                continue;
            }
            if (stripped.equalsIgnoreCase("options:") || stripped.equalsIgnoreCase("optional arguments:")) {
                continue;
            }
            if (!stripped.startsWith("-")) {
                continue;
            }
            Matcher splitM = OPTION_DESC_SPLIT.matcher(line);
            if (!splitM.matches()) {
                continue;
            }
            String optPart = splitM.group(1).trim();
            String descPart = splitM.group(2).trim();
            Matcher longM = LONG_OPT.matcher(optPart);
            String longId = null;
            String primaryLong = null;
            while (longM.find()) {
                longId = longM.group(1);
                primaryLong = longM.group(0);
            }
            if (longId == null) {
                Matcher sm = SHORT_OPT.matcher(optPart);
                if (!sm.find()) {
                    continue;
                }
                longId = "opt_" + sm.group(2);
                primaryLong = "-" + sm.group(2);
            }
            if (!seenLong.add(longId)) {
                continue;
            }
            String shortFlag = null;
            Matcher sm = SHORT_OPT.matcher(optPart);
            while (sm.find()) {
                shortFlag = sm.group(2);
            }
            boolean expects = optionExpectsValue(optPart);
            out.add(new ParsedOption(longId, primaryLong, shortFlag, expects, descPart));
        }
        return out;
    }

    private static boolean optionExpectsValue(String optPart) {
        if (optPart.contains("=")) {
            return true;
        }
        if (optPart.contains("<") && optPart.contains(">")) {
            return true;
        }
        // 行尾为 FILE、PATH 等大写占位
        return optPart.matches(".*\\s[A-Z][A-Z0-9_]{1,24}$");
    }

    /**
     * 生成 Camunda/JUEL 表达式字符串：字面量可执行段 + 各选项段。
     */
    record NamedOption(ParsedOption option, String paramKey) {
    }

    static String buildCommandTemplate(String executable, List<NamedOption> named) {
        String exe = executable == null ? "" : executable.trim();
        StringBuilder sb = new StringBuilder();
        sb.append(exe);
        for (NamedOption no : named) {
            ParsedOption o = no.option();
            String param = no.paramKey();
            if (o.expectsValue()) {
                String flagPrefix = o.primaryLongFlag();
                if (!flagPrefix.startsWith("--") && !flagPrefix.startsWith("-")) {
                    flagPrefix = "--" + o.longId();
                }
                if (flagPrefix.contains("=")) {
                    int eq = flagPrefix.indexOf('=');
                    String left = flagPrefix.substring(0, eq);
                    sb.append(" ").append(left).append("=${").append(param).append("}");
                } else {
                    sb.append(" ").append(flagPrefix).append(" ${").append(param).append("}");
                }
            } else {
                String flag = o.primaryLongFlag().strip();
                sb.append(" ${").append(param).append(" ? ' ").append(flag).append("' : ''}");
            }
        }
        return sb.toString();
    }

    private static String uniquifyKey(String base, Set<String> used) {
        String k = base;
        int n = 2;
        while (used.contains(k)) {
            k = base + "_" + n;
            n++;
        }
        used.add(k);
        return k;
    }

    private static String slugKey(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        if (StringUtils.isBlank(s)) {
            return "cli_component";
        }
        return s;
    }
}
