package com.kiwi.project.bpm.utils;

import com.kiwi.common.process.ProcessHelper;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过 {@link ProcessBuilder} 执行「help 命令」获取 stdout，从常见 GNU/类 GNU 风格 help 文本中抽取选项，
 * 生成继承 shell 的 {@link BpmComponent}：每个选项对应 {@code cli_*} 输入参数，并追加隐藏的 {@code command}。
 */
public final class CliHelpParser {

    private static final Pattern LONG_OPT = Pattern.compile("--([a-zA-Z][-a-zA-Z0-9]*)");
    /**
     * 单横线后「单词」形选项（如 {@code -FlipGain}、{@code -Dname=value}）。{@link #SHORT_OPT} 使用 {@code \\b}，
     * 只能匹配 {@code -x} 单字符，无法覆盖整段 {@code -FlipGain}。
     * 单字符 {@code -v} 仍走 SHORT_OPT，故此处要求选项名至少两个字符（首字母 + 至少一字）。
     */
    private static final Pattern SINGLE_DASH_WORD_OPT = Pattern.compile(
            "^-([a-zA-Z][-a-zA-Z0-9_]{1,})(?:=\\S+)?$");
    private static final Pattern SHORT_OPT = Pattern.compile("(^|[\\s,])-([a-zA-Z0-9])\\b");
    /** 行内描述列与选项列之间的分隔：至少两个空格 */
    private static final Pattern OPTION_DESC_SPLIT = Pattern.compile(
            "^\\s*(.+?)\\s{2,}([^\\s].*)$");
    /**
     * 仅含逗号分隔的若干短/长选项片段（不含尾随逗号），用于判断「第一个空格」是否在标志列与描述之间。
     * 注意 {@code -\S+} 会把 {@code -h,} 整块吃掉，故不用。
     */
    private static final Pattern FLAGS_ONLY_LINE = Pattern.compile(
            "^(?:(?:-[a-zA-Z0-9#][-a-zA-Z0-9_.]*|--[a-zA-Z][-a-zA-Z0-9]*)(?:\\s*,\\s*(?:-[a-zA-Z0-9#][-a-zA-Z0-9_.]*|--[a-zA-Z][-a-zA-Z0-9]*))*)$");

    /** {@link BpmComponent#getSource()}：CLI help 生成的组件来源标识，用于与 {@code classpath}/{@code xbpm} 区分 */
    public static final String COMPONENT_SOURCE = "cli-help";

    /** 隐藏的「executable 前缀」参数 key，仅在 CLI 生成的组件上出现，用于「重新生成 command」 */
    public static final String EXECUTABLE_PARAM_KEY = "__command";
    /** command 模板参数 key（与 shell 父组件约定） */
    public static final String COMMAND_PARAM_KEY = "command";

    /** {@link BpmComponentParameter#getAdditionalOption()} 的约定子键 */
    public static final String OPT_PRIMARY_LONG_FLAG = "primaryLongFlag";
    public static final String OPT_EXPECTS_VALUE = "expectsValue";
    public static final String OPT_SHORT_FLAG = "shortFlag";
    public static final String OPT_LONG_ID = "longId";
    public static final String OPT_FROM_CLI_HELP = "fromCliHelp";

    /** 拼装 command 时跳过的保留输入参数 key（来自 shell 父组件 + 自身控制字段） */
    private static final Set<String> RESERVED_INPUT_KEYS = Set.of(
            COMMAND_PARAM_KEY, EXECUTABLE_PARAM_KEY,
            "directory", "waitFlag", "redirectError", "cleanEnv");

    /** help 命令最大长度（防注入滥用） */
    private static final int MAX_HELP_COMMAND_LEN = 4000;
    /** 前端直接传入的 help 全文最大长度 */
    private static final int MAX_HELP_TEXT_LEN = 2_000_000;
    private static final long HELP_COMMAND_TIMEOUT_SEC = 60L;

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
     * 唯一入口：执行 {@code helpCommand}（如 {@code docker --help}）获取 help 文本，再生成组件草稿；
     * 名称、key、分组、描述、command 模板前缀等均由后端根据命令推导默认值。
     *
     * @param helpCommand  完整命令行字符串（Windows 下经 {@code cmd.exe /c}，其它平台经 {@code sh -c} 执行）
     * @param shellParentId shell 父组件在库中的 id
     */
    public static BpmComponent buildComponent(String helpCommand, String shellParentId) {
        return buildComponent(helpCommand, shellParentId, null);
    }

    /**
     * 与 {@link #buildComponent(String, String)} 相同，但若 {@code helpTextOverride} 非空则不再执行命令，
     * 直接使用其中内容作为 help 输出解析（适用于前端粘贴 help 全文）。
     *
     * @param helpTextOverride 可选；非空时作为 help 正文，忽略对 {@code helpCommand} 的执行
     */
    public static BpmComponent buildComponent(String helpCommand, String shellParentId, String helpTextOverride) {
        if (StringUtils.isBlank(helpCommand)) {
            throw new IllegalArgumentException("helpCommand 不能为空");
        }
        String trimmed = helpCommand.trim();
        if (trimmed.length() > MAX_HELP_COMMAND_LEN) {
            throw new IllegalArgumentException("helpCommand 过长（最大 " + MAX_HELP_COMMAND_LEN + " 字符）");
        }
        final String helpText;
        String pastedHelp = StringUtils.trimToNull(helpTextOverride);
        if (pastedHelp != null) {
            if (pastedHelp.length() > MAX_HELP_TEXT_LEN) {
                throw new IllegalArgumentException("helpText 过长（最大 " + MAX_HELP_TEXT_LEN + " 字符）");
            }
            helpText = pastedHelp;
        } else {
            try {
                helpText = runHelpCommand(trimmed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CliHelpExecutionException("执行 help 命令被中断", e);
            } catch (IOException e) {
                throw new CliHelpExecutionException("执行 help 命令失败: " + e.getMessage(), e);
            } catch (TimeoutException e) {
                throw new CliHelpExecutionException("执行 help 命令超时（" + HELP_COMMAND_TIMEOUT_SEC + "s）", e);
            }
        }
        if (StringUtils.isBlank(helpText)) {
            throw new CliHelpExecutionException("help 命令无输出");
        }
        String executable = defaultExecutablePrefix(trimmed);
        if (StringUtils.isBlank(executable)) {
            executable = "command";
        }
        String compName = executable + " CLI";
        String description = "从 help 命令生成: " + trimmed;
        String group = "命令行";
        String normalizedCmd = normalizeHelpCommandForSourceKey(trimmed);
        String sourceKey = cliSourceKey(normalizedCmd);
        return buildFromHelpText(
                helpText, executable, compName,  group, description, shellParentId, sourceKey);
    }

    /** 与同一条 help 命令对应的稳定来源键（用于入库判重）。 */
    public static String cliSourceKey(String normalizedHelpCommand) {
        return "cli-help:v1|" + normalizedHelpCommand;
    }

    static String normalizeHelpCommandForSourceKey(String helpCommand) {
        return helpCommand.trim().replaceAll("\\s+", " ");
    }

    /**
     * Windows：{@code cmd.exe /c &lt;整行&gt;}；其它：{@code sh -c &lt;整行&gt;}。合并 stderr 到 stdout，超时 60s。
     * 排空管道逻辑见 {@link ProcessHelper#waitForDrain(Process, boolean, long, TimeUnit)}。
     */
    static String runHelpCommand(String helpCommand) throws IOException, InterruptedException, TimeoutException {
        ProcessBuilder pb = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", helpCommand)
                : new ProcessBuilder("sh", "-c", helpCommand);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        ProcessHelper.StreamResult r = ProcessHelper.waitForDrain(process, true, HELP_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS);
        Charset charset = Charset.defaultCharset();
        String text = new String(r.stdout(), charset);
        if (StringUtils.isBlank(text) && r.exitCode() != 0) {
            throw new CliHelpExecutionException("help 命令失败，退出码 " + r.exitCode());
        }
        return text;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    /**
     * 去掉末尾的 {@code --help}、{@code -h} 等，得到写入 command 模板的字面量前缀（可含空格，如 {@code docker compose}）。
     */
    static String defaultExecutablePrefix(String helpCommand) {
        String s = helpCommand.trim();
        while (true) {
            String t = s.replaceAll("(?i)\\s+(--help-all|--help|-help|-h|/\\?|/help)\\s*$", "").trim();
            if (t.equals(s)) {
                break;
            }
            s = t;
        }
        return s;
    }

    private static BpmComponent buildFromHelpText(
            String helpText,
            String executable,
            String name,
            String group,
            String description,
            String shellParentId,
            String sourceKey
    ) {
        List<ParsedOption> options = parseOptions(helpText);
        BpmComponent c = new BpmComponent();
        c.setParentId(shellParentId);
        c.setName(name);
        c.setDescription(description);
        c.setGroup(group);
        c.setSource(COMPONENT_SOURCE);
        c.setSourceKey(sourceKey);
        List<BpmComponentParameter> inputs = new ArrayList<>();
        for (ParsedOption o : options) {
            String paramKey = o.longId().replace('-', '_');
            BpmComponentParameter p = new BpmComponentParameter();
            p.setKey(paramKey);
            p.setName(paramKey);
            p.setDescription(o.description());
            p.setImportant(true);
            p.setRequired(false);
            if (!o.expectsValue()) {
                p.setDefaultValue("false");
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(OPT_PRIMARY_LONG_FLAG, o.primaryLongFlag());
            meta.put(OPT_EXPECTS_VALUE, o.expectsValue());
            if (StringUtils.isNotBlank(o.shortFlag())) {
                meta.put(OPT_SHORT_FLAG, o.shortFlag());
            }
            meta.put(OPT_LONG_ID, o.longId());
            meta.put(OPT_FROM_CLI_HELP, true);
            p.setAdditionalOption(meta);
            inputs.add(p);
        }
        inputs.add(buildExecutableParam(executable));
        BpmComponentParameter command = new BpmComponentParameter();
        command.setKey(COMMAND_PARAM_KEY);
        command.setName(COMMAND_PARAM_KEY);
        command.setDescription("由 CLI 选项拼装的完整命令（覆盖父组件 command；Camunda 输入中可使用 JUEL 表达式）");
        command.setGroup("脚本");
        command.setHidden(true);
        command.setImportant(false);
        command.setDefaultValue(buildCommandTemplateFromParams(executable, inputs));
        inputs.add(command);
        c.setInputParameters(inputs);
        c.setOutputParameters(null);
        return c;
    }

    /** 构造隐藏的 {@value #EXECUTABLE_PARAM_KEY} 参数，用于「重新生成 command」时取回 executable 前缀。 */
    public static BpmComponentParameter buildExecutableParam(String executable) {
        BpmComponentParameter p = new BpmComponentParameter();
        p.setKey(EXECUTABLE_PARAM_KEY);
        p.setName(EXECUTABLE_PARAM_KEY);
        p.setDescription("CLI 重建 command 时使用的 executable 前缀（自动维护，请勿手动修改）");
        p.setGroup("脚本");
        p.setHidden(true);
        p.setReadonly(true);
        p.setImportant(false);
        p.setDefaultValue(executable == null ? "" : executable.trim());
        return p;
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
            String[] optDesc = splitOptDescParts(line);
            if (optDesc == null) {
                continue;
            }
            String optPart = optDesc[0];
            String descPart = optDesc[1];
            Matcher longM = LONG_OPT.matcher(optPart);
            String longId = null;
            String primaryLong = null;
            while (longM.find()) {
                longId = longM.group(1);
                primaryLong = longM.group(0);
            }
            String shortFlag = null;
            if (longId != null) {
                Matcher smShort = SHORT_OPT.matcher(optPart);
                while (smShort.find()) {
                    shortFlag = smShort.group(2);
                }
            } else {
                Matcher multi = SINGLE_DASH_WORD_OPT.matcher(optPart);
                if (multi.matches()) {
                    longId = multi.group(1);
                    primaryLong = multi.group(0);
                } else {
                    Matcher sm = SHORT_OPT.matcher(optPart);
                    if (!sm.find()) {
                        continue;
                    }
                    longId = "opt_" + sm.group(2);
                    primaryLong = "-" + sm.group(2);
                    Matcher sm2 = SHORT_OPT.matcher(optPart);
                    while (sm2.find()) {
                        shortFlag = sm2.group(2);
                    }
                }
            }
            if (!seenLong.add(longId)) {
                continue;
            }
            boolean expects = true;//optionExpectsValue(optPart);
            out.add(new ParsedOption(longId, primaryLong, shortFlag, expects, descPart));
        }
        return out;
    }

    /**
     * 拆出「选项片段」与描述。优先匹配 GNU 风格两列（至少两空格）；否则尝试制表符、行内双空格、
     * 仅标志位后的单空格、可选一个 metavar 词等，使不符合 {@link #OPTION_DESC_SPLIT} 的行仍能解析。
     *
     * @return {@code [optPart, descPart]}，无法识别为选项行时返回 {@code null}
     */
    static String[] splitOptDescParts(String line) {
        String stripped = line.stripLeading();
        if (!stripped.startsWith("-")) {
            return null;
        }
        Matcher splitM = OPTION_DESC_SPLIT.matcher(line);
        if (splitM.matches()) {
            return new String[]{splitM.group(1).trim(), splitM.group(2).trim()};
        }
        int tab = line.indexOf('\t');
        if (tab > 0) {
            String left = line.substring(0, tab).trim();
            String right = line.substring(tab + 1).trim();
            if (left.startsWith("-")) {
                return new String[]{left, right};
            }
        }
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == ' ' && line.charAt(i + 1) == ' ') {
                String left = line.substring(0, i).trim();
                String right = line.substring(i).trim();
                if (left.startsWith("-")) {
                    return new String[]{left, right};
                }
            }
        }
        int sp = stripped.indexOf(' ');
        if (sp > 0) {
            String left = stripped.substring(0, sp);
            String right = stripped.substring(sp + 1).trim();
            if (FLAGS_ONLY_LINE.matcher(left).matches()) {
                int sp2 = right.indexOf(' ');
                String firstW = sp2 > 0 ? right.substring(0, sp2) : right;
                String afterW = sp2 > 0 ? right.substring(sp2 + 1).trim() : "";
                if (isLikelyMetavar(firstW) && !afterW.isEmpty()) {
                    return new String[]{left + " " + firstW, afterW};
                }
                if (isLikelyMetavar(firstW) && afterW.isEmpty()) {
                    return new String[]{left + " " + firstW, ""};
                }
                return new String[]{left, right};
            }
            if (left.startsWith("-")) {
                return new String[]{left, right};
            }
        }
        return new String[]{stripped, ""};
    }

    private static boolean isLikelyMetavar(String w) {
        if (StringUtils.isBlank(w)) {
            return false;
        }
        if (w.startsWith("<") && w.endsWith(">")) {
            return true;
        }
        if (w.length() <= 32 && w.equals(w.toUpperCase(Locale.ROOT)) && w.matches("[A-Z0-9_]+")) {
            return true;
        }
        return false;
    }

    private static boolean optionExpectsValue(String optPart) {
        if (optPart.contains("=")) {
            return true;
        }
        if (optPart.contains("<") && optPart.contains(">")) {
            return true;
        }
        return optPart.matches(".*\\s[A-Z][A-Z0-9_]{1,24}$");
    }

    record NamedOption(ParsedOption option, String paramKey) {
    }

    /**
     * 根据当前输入参数列表与 executable 前缀重建 JUEL 命令模板。
     * <p>跳过 {@link #RESERVED_INPUT_KEYS} 中的保留项以及 {@link BpmComponentParameter#isHidden() 隐藏} 参数；
     * 每个参数从 {@link BpmComponentParameter#getAdditionalOption() additionalOption} 读取
     * {@code primaryLongFlag} / {@code expectsValue}；缺失时按 key 与默认值做兜底推断：
     * </p>
     * <ul>
     *   <li>{@code primaryLongFlag} 兜底：{@code "--" + key.replace('_','-')}</li>
     *   <li>{@code expectsValue} 兜底：{@code !"false".equalsIgnoreCase(defaultValue)}</li>
     * </ul>
     */
    public static String buildCommandTemplateFromParams(String executable, List<BpmComponentParameter> inputs) {
        String exe = executable == null ? "" : executable.trim();
        StringBuilder sb = new StringBuilder(exe);
        if (inputs == null) {
            return sb.toString();
        }
        for (BpmComponentParameter p : inputs) {
            String key = p.getKey();
            if (StringUtils.isBlank(key) || RESERVED_INPUT_KEYS.contains(key)) {
                continue;
            }
            if (p.isHidden()) {
                continue;
            }
            String flag = readPrimaryLongFlag(p);
            boolean expectsValue = readExpectsValue(p);
            appendJuelFragment(sb, key, flag, expectsValue);
        }
        return sb.toString();
    }

    /** 旧入口保留：把内部 {@link NamedOption} 列表映射为参数列表后委托到 {@link #buildCommandTemplateFromParams}。 */
    static String buildCommandTemplate(String executable, List<NamedOption> named) {
        List<BpmComponentParameter> inputs = new ArrayList<>();
        for (NamedOption no : named) {
            ParsedOption o = no.option();
            BpmComponentParameter p = new BpmComponentParameter();
            p.setKey(no.paramKey());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(OPT_PRIMARY_LONG_FLAG, o.primaryLongFlag());
            meta.put(OPT_EXPECTS_VALUE, o.expectsValue());
            meta.put(OPT_LONG_ID, o.longId());
            p.setAdditionalOption(meta);
            inputs.add(p);
        }
        return buildCommandTemplateFromParams(executable, inputs);
    }

    /**
     * 从 {@link #COMMAND_PARAM_KEY} 参数 {@code defaultValue} 中截取 executable 前缀。
     * 取字符串首个 {@code ${} 之前的内容并 {@link String#trim() 去空白}；找不到则整串 trim 返回。
     */
    public static String extractExecutableFromTemplate(String commandTemplate) {
        if (commandTemplate == null) {
            return "";
        }
        int idx = commandTemplate.indexOf("${");
        String head = idx >= 0 ? commandTemplate.substring(0, idx) : commandTemplate;
        return head.trim();
    }

    private static String readPrimaryLongFlag(BpmComponentParameter p) {
        Map<String, Object> meta = p.getAdditionalOption();
        if (meta != null) {
            Object v = meta.get(OPT_PRIMARY_LONG_FLAG);
            if (v instanceof String s && StringUtils.isNotBlank(s)) {
                return s.trim();
            }
        }
        return "--" + p.getKey().replace('_', '-');
    }

    private static boolean readExpectsValue(BpmComponentParameter p) {
        Map<String, Object> meta = p.getAdditionalOption();
        if (meta != null && meta.containsKey(OPT_EXPECTS_VALUE)) {
            Object v = meta.get(OPT_EXPECTS_VALUE);
            if (v instanceof Boolean b) {
                return b;
            }
            if (v instanceof String s) {
                return !"false".equalsIgnoreCase(s.trim());
            }
        }
        return !"false".equalsIgnoreCase(StringUtils.trimToEmpty(p.getDefaultValue()));
    }

    private static void appendJuelFragment(StringBuilder sb, String param, String flagPrefixIn, boolean expectsValue) {
        String flagPrefix = flagPrefixIn;
        if (flagPrefix == null || flagPrefix.isBlank()) {
            flagPrefix = "--" + param.replace('_', '-');
        }
        if (!flagPrefix.startsWith("-")) {
            flagPrefix = "--" + flagPrefix;
        }
        if (expectsValue) {
            if (flagPrefix.contains("=")) {
                int eq = flagPrefix.indexOf('=');
                String left = flagPrefix.substring(0, eq);
                // Camunda JUEL 将 + 视为算术运算，字符串拼接需用 concat，否则会报 Cannot coerce ... to Double
                sb.append(" ${not empty ").append(param).append(" ? ' ").append(left).append("='.concat(").append(param).append(") : ''}");
            } else {
                sb.append(" ${not empty ").append(param).append(" ? ' ").append(flagPrefix).append(" '.concat(").append(param).append(") : ''}");
            }
        } else {
            sb.append(" ${").append(param).append(" ? ' ").append(flagPrefix.strip()).append("' : ''}");
        }
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
