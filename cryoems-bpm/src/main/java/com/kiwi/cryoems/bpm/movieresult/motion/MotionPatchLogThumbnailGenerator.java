package com.kiwi.cryoems.bpm.movieresult.motion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 对齐 cyroems {@code MovieService#createPatchLog} / {@code SoftwareService#patch_log_png}。
 */
@Component
@Slf4j
public class MotionPatchLogThumbnailGenerator {

    @Value("${cryoems.bpm.motion-patch-png-command:motion_patch_png.sh}")
    private String motionPatchPngCommand;

    @Value("${cryoems.bpm.png-command-timeout-seconds:300}")
    private long commandTimeoutSeconds;

    public void generate(MotionPaths paths) throws IOException, InterruptedException {
        Path input = Path.of(paths.localLog());
        if (!Files.isRegularFile(input)) {
            throw new IllegalStateException("motion patch log file not exist: " + paths.localLog());
        }
        Path output = Path.of(paths.patchLogImage());
        Files.createDirectories(output.getParent());
        runPatchLogPng(paths.localLog(), paths.patchLogImage());
    }

    private void runPatchLogPng(String input, String output) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(motionPatchPngCommand, "-i", input, "-o", output);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        log.info("执行命令: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        boolean finished = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    motionPatchPngCommand + " 超时（" + commandTimeoutSeconds + "s）: -i " + input);
        }
        int code = process.exitValue();
        if (code != 0) {
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    motionPatchPngCommand
                            + " 退出码 "
                            + code
                            + " -i "
                            + input
                            + " -o "
                            + output
                            + (err.isBlank() ? "" : "\nstderr: " + err));
        }
        log.debug("命令完成: {} -i {} -o {}", motionPatchPngCommand, input, output);
    }
}
