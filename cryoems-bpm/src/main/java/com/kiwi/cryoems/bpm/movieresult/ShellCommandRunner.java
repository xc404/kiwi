package com.kiwi.cryoems.bpm.movieresult;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ShellCommandRunner {

    @Value("${cryoems.bpm.png-command-timeout-seconds:300}")
    private long commandTimeoutSeconds;

    public void run(String command, String input, String output) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command, "-i", input, "-o", output);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = pb.start();
        boolean finished = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(command + " 超时（" + commandTimeoutSeconds + "s）: -i " + input);
        }
        int code = process.exitValue();
        if (code != 0) {
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    command
                            + " 退出码 "
                            + code
                            + " -i "
                            + input
                            + " -o "
                            + output
                            + (err.isBlank() ? "" : "\nstderr: " + err));
        }
        log.debug("命令完成: {} -i {} -o {}", command, input, output);
    }
}
