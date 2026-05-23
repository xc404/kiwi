package com.kiwi.cryoems.bpm.movie.result.ctf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 对齐 cyroems {@code MovieService#createCtf} / {@code SoftwareService#ctf_png}。
 */
@Component
@Slf4j
public class CtfThumbnailGenerator {

    @Value("${cryoems.bpm.ctf-png-command:ctf_png.sh}")
    private String ctfPngCommand;

    @Value("${cryoems.bpm.png-command-timeout-seconds:300}")
    private long commandTimeoutSeconds;

    public void generate(CtfPaths paths) throws IOException, InterruptedException {
        Path output = Path.of(paths.image());
        Files.createDirectories(output.getParent());
        runCtfPng(paths.outputMrc(), paths.image());
    }

    private void runCtfPng(String input, String output) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(ctfPngCommand, "-i", input, "-o", output);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        log.info("执行命令: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        boolean finished = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(ctfPngCommand + " 超时（" + commandTimeoutSeconds + "s）: -i " + input);
        }
        int code = process.exitValue();
        if (code != 0) {
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    ctfPngCommand
                            + " 退出码 "
                            + code
                            + " -i "
                            + input
                            + " -o "
                            + output
                            + (err.isBlank() ? "" : "\nstderr: " + err));
        }
        log.debug("命令完成: {} -i {} -o {}", ctfPngCommand, input, output);
    }
}
