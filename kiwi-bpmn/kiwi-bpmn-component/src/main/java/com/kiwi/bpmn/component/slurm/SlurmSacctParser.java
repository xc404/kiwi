package com.kiwi.bpmn.component.slurm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析 {@code sacct --parsable2} 输出，判断作业是否终态及用于 {@link SlurmResult} 的退出码。
 */
public final class SlurmSacctParser {

    private static final Set<String> ACTIVE_STATES =
            Set.of("PENDING", "RUNNING", "COMPLETING", "CONFIGURING", "SUSPENDED", "PREEMPTED", "REQUEUED");

    private static final Set<String> FAIL_STATES =
            Set.of("FAILED", "CANCELLED", "TIMEOUT", "NODE_FAIL", "OUT_OF_MEMORY", "DEADLINE");

    private SlurmSacctParser() {}

    public record SacctLine(String jobId, String state, String exitCodeField) {}

    /**
     * 按 {@code |} 拆分 sacct 行（parsable2）；忽略空行与表头误传。
     */
    public static List<SacctLine> parseLines(String raw) {
        List<SacctLine> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            String[] cols = t.split("\\|", -1);
            if (cols.length < 3) {
                continue;
            }
            String jobId = cols[0].trim();
            String state = cols[1].trim().toUpperCase(Locale.ROOT);
            String exitCode = cols[2].trim();
            if ("JOBID".equalsIgnoreCase(jobId)) {
                continue;
            }
            out.add(new SacctLine(jobId, state, exitCode));
        }
        return out;
    }

    /**
     * @param requestedJobId sbatch 返回的裸 id（如 {@code 12345}）
     */
    public static SacctResolution resolveForJob(List<SacctLine> lines, String requestedJobId) {
        if (requestedJobId == null || requestedJobId.isBlank()) {
            return SacctResolution.notYetTerminal();
        }
        List<SacctLine> relevant = new ArrayList<>();
        for (SacctLine l : lines) {
            if (matchesJob(l.jobId(), requestedJobId)) {
                relevant.add(l);
            }
        }
        if (relevant.isEmpty()) {
            return SacctResolution.notYetTerminal();
        }
        for (SacctLine l : relevant) {
            if (ACTIVE_STATES.contains(l.state())) {
                return SacctResolution.notYetTerminal();
            }
        }
        for (SacctLine l : relevant) {
            if (FAIL_STATES.contains(l.state())) {
                int ec = parsePrimaryExitCode(l.exitCodeField());
                if (ec == 0) {
                    ec = mapStateToSyntheticExit(l.state());
                }
                return SacctResolution.terminalFailure(ec, l.state());
            }
        }
        SacctLine batch = null;
        for (SacctLine l : relevant) {
            if (l.jobId().equals(requestedJobId + ".batch")) {
                batch = l;
                break;
            }
        }
        if (batch != null && "COMPLETED".equals(batch.state())) {
            int ec = parsePrimaryExitCode(batch.exitCodeField());
            if (ec == 0) {
                return SacctResolution.terminalSuccess();
            }
            return SacctResolution.terminalFailure(ec, batch.state());
        }
        boolean allCompletedLike = true;
        for (SacctLine l : relevant) {
            if (!"COMPLETED".equals(l.state()) && !"COMPLETING".equals(l.state())) {
                allCompletedLike = false;
                break;
            }
        }
        if (allCompletedLike && batch == null) {
            int worst = 0;
            for (SacctLine l : relevant) {
                if ("COMPLETED".equals(l.state())) {
                    worst = Math.max(worst, parsePrimaryExitCode(l.exitCodeField()));
                }
            }
            if (worst == 0) {
                return SacctResolution.terminalSuccess();
            }
            return SacctResolution.terminalFailure(worst, "COMPLETED");
        }
        return SacctResolution.notYetTerminal();
    }

    private static boolean matchesJob(String jobIdCol, String requestedJobId) {
        if (jobIdCol == null || jobIdCol.isBlank()) {
            return false;
        }
        String j = jobIdCol.trim();
        if (j.equals(requestedJobId)) {
            return true;
        }
        return j.startsWith(requestedJobId + ".");
    }

    private static int parsePrimaryExitCode(String exitCodeField) {
        if (exitCodeField == null || exitCodeField.isBlank()) {
            return 0;
        }
        String s = exitCodeField.trim();
        int colon = s.indexOf(':');
        String first = colon >= 0 ? s.substring(0, colon) : s;
        first = first.replaceAll("\\s+", "");
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int mapStateToSyntheticExit(String state) {
        if ("CANCELLED".equals(state)) {
            return 143;
        }
        if ("TIMEOUT".equals(state)) {
            return 124;
        }
        if ("NODE_FAIL".equals(state)) {
            return 125;
        }
        return 1;
    }

    public static final class SacctResolution {
        private final boolean terminal;
        private final boolean success;
        private final int commandExitCode;
        private final String slurmState;

        private SacctResolution(boolean terminal, boolean success, int commandExitCode, String slurmState) {
            this.terminal = terminal;
            this.success = success;
            this.commandExitCode = commandExitCode;
            this.slurmState = slurmState;
        }

        public static SacctResolution notYetTerminal() {
            return new SacctResolution(false, false, 0, null);
        }

        public static SacctResolution terminalSuccess() {
            return new SacctResolution(true, true, 0, "COMPLETED");
        }

        public static SacctResolution terminalFailure(int commandExitCode, String slurmState) {
            return new SacctResolution(true, false, commandExitCode, slurmState);
        }

        public boolean terminal() {
            return terminal;
        }

        public boolean success() {
            return success;
        }

        public int commandExitCode() {
            return commandExitCode;
        }

        public String slurmState() {
            return slurmState;
        }
    }
}
