package com.kiwi.cryoems.bpm.movie.result.motion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 对齐 cyroems {@code MovieService#createMotionMrc} / {@code SoftwareService#mrc_png}。
 */
@Component
@Slf4j
public class MotionThumbnailGenerator {

    @Value("${cryoems.bpm.mrc-png-command:mrc_png.sh}")
    private String mrcPngCommand;

    @Value("${cryoems.bpm.png-command-timeout-seconds:300}")
    private long commandTimeoutSeconds;

    public void generate(MotionPaths paths) throws IOException, InterruptedException {
        Path output = Path.of(paths.mrcImage());
        Files.createDirectories(output.getParent());
        runMrcPng(paths.dwMrc(), paths.mrcImage());
    }

    private void runMrcPng(String input, String output) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(mrcPngCommand, "-i", input, "-o", output);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        log.info("执行命令: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        boolean finished = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(mrcPngCommand + " 超时（" + commandTimeoutSeconds + "s）: -i " + input);
        }
        int code = process.exitValue();
        if (code != 0) {
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    mrcPngCommand
                            + " 退出码 "
                            + code
                            + " -i "
                            + input
                            + " -o "
                            + output
                            + (err.isBlank() ? "" : "\nstderr: " + err));
        }
        log.debug("命令完成: {} -i {} -o {}", mrcPngCommand, input, output);
    }
}
