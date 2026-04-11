package com.kiwi.project.bpm.utils;

import com.kiwi.common.process.ProcessHelper;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern SHORT_OPT = Pattern.compile("(^|[\\s,])-([a-zA-Z0-9])\\b");
    /** 行内描述列与选项列之间的分隔：至少两个空格 */
    private static final Pattern OPTION_DESC_SPLIT = Pattern.compile(
            "^\\s*(.+?)\\s{2,}([^\\s].*)$");

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
        String compKey = slugKey(executable);
        String description = "从 help 命令生成: " + trimmed;
        String group = "命令行";
        return buildFromHelpText(helpText, executable, compName, compKey, group, description, shellParentId);
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
            String key,
            String group,
            String description,
            String shellParentId
    ) {
        List<ParsedOption> options = parseOptions(helpText);
        BpmComponent c = new BpmComponent();
        c.setParentId(shellParentId);
        c.setKey(key);
        c.setName(name);
        c.setDescription(description);
        c.setGroup(group);
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
        return optPart.matches(".*\\s[A-Z][A-Z0-9_]{1,24}$");
    }

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
