package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.process.ProcessHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 调用 Slurm {@code sacct} 查询作业状态（批量 JobID）。
 */
public final class SlurmSacctClient {

    private SlurmSacctClient() {}

    public static String queryJobBatch(List<String> jobIds, String executable, List<String> extraArgs)
            throws IOException, InterruptedException {
        if (jobIds == null || jobIds.isEmpty()) {
            return "";
        }
        String exe = executable != null && !executable.isBlank() ? executable.trim() : "sacct";
        List<String> cmd = new ArrayList<>();
        cmd.add(exe);
        cmd.add("-j");
        cmd.add(String.join(",", jobIds));
        cmd.add("--noheader");
        cmd.add("--parsable2");
        cmd.add("-o");
        cmd.add("JobID,State,ExitCode");
        if (extraArgs != null) {
            for (String a : extraArgs) {
                if (a != null && !a.isBlank()) {
                    cmd.add(a.trim());
                }
            }
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        ProcessHelper.StreamResult drained;
        try {
            drained = ProcessHelper.waitForDrain(p, false, 0, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            p.destroyForcibly();
            throw new IOException("sacct timed out waiting for process", e);
        }
        int ec = drained.exitCode();
        String stdout = new String(drained.stdout(), StandardCharsets.UTF_8);
        if (ec != 0) {
            String stderr = new String(drained.stderr(), StandardCharsets.UTF_8);
            throw new IOException("sacct exited with " + ec + ": " + stderr);
        }
        return stdout;
    }
}
